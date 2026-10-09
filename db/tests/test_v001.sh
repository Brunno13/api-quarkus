#!/usr/bin/env bash
set -euo pipefail

# Safety guard: this suite creates and drops temporary databases.
# Run only against a dedicated MongoDB test instance.

if [[ "${MONGODB_TEST_ALLOW_DROP:-}" != "1" ]]; then
  echo "ERROR: MONGODB_TEST_ALLOW_DROP=1 is required." >&2
  echo "Use a dedicated MongoDB test instance." >&2
  exit 1
fi

: "${MONGODB_URI:?MONGODB_URI is required}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
migration="${MIGRATION_SCRIPT:-${script_dir}/../migrations/V001__users_email_unique.js}"

if [[ ! -f "$migration" ]]; then
  echo "Migration file not found: $migration" >&2
  exit 1
fi

test_db="api_quarkus_v001_ci_$(date +%s)_$$"

duplicates_db="${test_db}_duplicates"

invalid_email_dbs=(
  "${test_db}_not_normalized"
  "${test_db}_missing_email"
  "${test_db}_empty_email"
  "${test_db}_wrong_type"
)

mongo_eval() {
  TEST_DB="$test_db" mongosh "$MONGODB_URI" --quiet --eval "$1"
}

cleanup() {
  for db_name in "$test_db" "$duplicates_db" "${invalid_email_dbs[@]}"; do
    TEST_DB="$db_name" \
      mongosh "$MONGODB_URI" --quiet \
      --eval 'db.getSiblingDB(process.env.TEST_DB).dropDatabase()' \
      >/dev/null || true
  done
}

trap cleanup EXIT

run_migration() {
  MONGODB_DATABASE="$test_db" \
    mongosh --nodb --quiet --file "$migration"
}

echo "===== FIRST EXECUTION ====="
run_migration

original_date="$(mongo_eval '
  const d = db.getSiblingDB(process.env.TEST_DB);
  const record = d.schema_migrations.findOne({
    _id: "V001__users_email_unique"
  });

  if (!record) {
    throw new Error("Migration record missing");
  }

  print(record.appliedAt.toISOString());
')"

echo "===== IDEMPOTENCY ====="
run_migration

TEST_DB="$test_db" ORIGINAL_DATE="$original_date" \
  mongosh "$MONGODB_URI" --quiet --eval '
    const d = db.getSiblingDB(process.env.TEST_DB);

    const indexes = d.users.getIndexes().filter(
      i => i.name === "ux_users_email"
    );

    if (
      indexes.length !== 1 ||
      indexes[0].unique !== true ||
      indexes[0].key.email !== 1
    ) {
      throw new Error("Unique email index verification failed");
    }

    const records = d.schema_migrations.find({
      _id: "V001__users_email_unique"
    }).toArray();

    if (records.length !== 1) {
      throw new Error("Migration history verification failed");
    }

    if (
      records[0].appliedAt.toISOString() !==
      process.env.ORIGINAL_DATE
    ) {
      throw new Error("Migration timestamp was modified");
    }

    print("IDEMPOTENCY=PASS");

    d.users.insertOne({
      _id: "test-user-1",
      name: "User 1",
      email: "duplicate@example.com",
      roles: ["USER"]
    });

    let duplicateBlocked = false;

    try {
      d.users.insertOne({
        _id: "test-user-2",
        name: "User 2",
        email: "duplicate@example.com",
        roles: ["USER"]
      });
    } catch (error) {
      if (error.code !== 11000) {
        throw error;
      }
      duplicateBlocked = true;
    }

    if (
      !duplicateBlocked ||
      d.users.countDocuments({
        email: "duplicate@example.com"
      }) !== 1
    ) {
      throw new Error("Duplicate email verification failed");
    }

    print("DUPLICATE_EMAIL=PASS");
  '

echo "===== PREEXISTING DUPLICATES ====="

TEST_DB="$duplicates_db" \
  mongosh "$MONGODB_URI" --quiet --eval '
    const d = db.getSiblingDB(process.env.TEST_DB);

    d.users.insertMany([
      {
        _id: "existing-user-1",
        email: "duplicate@example.com"
      },
      {
        _id: "existing-user-2",
        email: "duplicate@example.com"
      }
    ]);

    if (d.users.countDocuments({
      email: "duplicate@example.com"
    }) !== 2) {
      throw new Error("Failed to prepare duplicate users");
    }
  '

if output="$(
  MONGODB_DATABASE="$duplicates_db" \
    mongosh --nodb --quiet --file "$migration" 2>&1
)"; then
  echo "$output"
  echo "FAIL: migration accepted preexisting duplicates" >&2
  exit 1
fi

if [[ "$output" != *"E11000"* ]]; then
  echo "$output"
  echo "FAIL: migration did not report E11000" >&2
  exit 1
fi

TEST_DB="$duplicates_db" \
  mongosh "$MONGODB_URI" --quiet --eval '
    const d = db.getSiblingDB(process.env.TEST_DB);

    const users = d.users.countDocuments({
      email: "duplicate@example.com"
    });

    const indexes = d.users.getIndexes().filter(
      index => index.name === "ux_users_email"
    );

    const migrations = d.schema_migrations.countDocuments({
      _id: "V001__users_email_unique"
    });

    if (users !== 2 || indexes.length !== 0 || migrations !== 0) {
      throw new Error("Migration failure changed database integrity");
    }

    print("PREEXISTING_DUPLICATES=PASS");
  '

test_invalid_email() {
  local database_name="$1"
  local test_name="$2"
  local document_json="$3"
  local expected_error="$4"

  echo "===== $test_name ====="

  TEST_DB="$database_name" \
  TEST_DOCUMENT="$document_json" \
    mongosh "$MONGODB_URI" --quiet --eval '
      const d = db.getSiblingDB(process.env.TEST_DB);
      const document = JSON.parse(process.env.TEST_DOCUMENT);

      d.users.insertOne(document);

      if (d.users.countDocuments({}) !== 1) {
        throw new Error("Failed to prepare invalid email fixture");
      }
    '

  local output

  if output="$(
    MONGODB_DATABASE="$database_name" \
      mongosh --nodb --quiet --file "$migration" 2>&1
  )"; then
    echo "FAIL: migration accepted invalid email" >&2
    exit 1
  fi

  if [[ "$output" != *"$expected_error"* ]]; then
    echo "$output" >&2
    echo "FAIL: unexpected migration error" >&2
    exit 1
  fi

  TEST_DB="$database_name" \
  TEST_DOCUMENT="$document_json" \
  TEST_NAME="$test_name" \
    mongosh "$MONGODB_URI" --quiet --eval '
      const d = db.getSiblingDB(process.env.TEST_DB);
      const original = JSON.parse(process.env.TEST_DOCUMENT);
      const persisted = d.users.findOne({ _id: original._id });

      const documentPreserved =
        persisted !== null &&
        JSON.stringify(persisted) === JSON.stringify(original);

      const indexCount = d.users.getIndexes().filter(
        index => index.name === "ux_users_email"
      ).length;

      const migrationCount = d.schema_migrations.countDocuments({
        _id: "V001__users_email_unique"
      });

      if (
        d.users.countDocuments({}) !== 1 ||
        !documentPreserved ||
        indexCount !== 0 ||
        migrationCount !== 0
      ) {
        throw new Error("Failed migration changed database state");
      }

      print(process.env.TEST_NAME + "=PASS");
    '
}

test_invalid_email \
  "${test_db}_not_normalized" \
  "NON_NORMALIZED_EMAIL" \
  '{"_id":"invalid-1","email":"  ADMIN@Example.COM  "}' \
  "Users collection contains non-normalized emails"

test_invalid_email \
  "${test_db}_missing_email" \
  "MISSING_EMAIL" \
  '{"_id":"invalid-2","name":"Missing Email"}' \
  "Users collection contains invalid emails"

test_invalid_email \
  "${test_db}_empty_email" \
  "EMPTY_EMAIL" \
  '{"_id":"invalid-3","email":""}' \
  "Users collection contains invalid emails"

test_invalid_email \
  "${test_db}_wrong_type" \
  "WRONG_EMAIL_TYPE" \
  '{"_id":"invalid-4","email":12345}' \
  "Users collection contains invalid emails"

echo "V001_AUTOMATED_TEST=PASS"
