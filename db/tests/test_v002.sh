
#!/usr/bin/env bash
set -euo pipefail

# DESTRUCTIVE TEST HARNESS.
# Run only against a dedicated, disposable MongoDB test instance.

if [[ "${MONGODB_TEST_ALLOW_DROP:-}" != "1" ]]; then
  echo "ERROR: MONGODB_TEST_ALLOW_DROP=1 is required." >&2
  exit 1
fi

: "${MONGODB_URI:?MONGODB_URI is required}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
v001="${script_dir}/../migrations/V001__users_email_unique.js"
v002="${script_dir}/../migrations/V002__user_credentials.js"

for migration in "$v001" "$v002"; do
  [[ -f "$migration" ]] || {
    echo "Missing migration: $migration" >&2
    exit 1
  }
done

prefix="api_quarkus_v002_ci_$(date +%s)_$$_${RANDOM}"
databases=()

cleanup() {
  for database_name in "${databases[@]}"; do
    [[ "$database_name" == "${prefix}"* ]] || continue

    TEST_DB="$database_name" \
      mongosh "$MONGODB_URI" --quiet \
      --eval 'db.getSiblingDB(process.env.TEST_DB).dropDatabase()' \
      >/dev/null 2>&1 || true
  done
}

trap cleanup EXIT

run_migration() {
  local migration="$1"
  local database_name="$2"

  MONGODB_URI="$MONGODB_URI" \
  MONGODB_DATABASE="$database_name" \
    mongosh --nodb --quiet --file "$migration"
}

mongo_eval() {
  local database_name="$1"
  local expression="$2"

  TEST_DB="$database_name" \
    mongosh "$MONGODB_URI" --quiet --eval "$expression"
}

expect_v002_failure() {
  local database_name="$1"
  local test_name="$2"
  local expected_message="$3"
  local output

  if output="$(run_migration "$v002" "$database_name" 2>&1)"; then
    echo "FAIL: $test_name unexpectedly succeeded" >&2
    exit 1
  fi

  if [[ "$output" != *"$expected_message"* ]]; then
    echo "$output" >&2
    echo "FAIL: unexpected error for $test_name" >&2
    exit 1
  fi

  echo "${test_name}=PASS"
}

echo "===== FRESH CREDENTIAL COLLECTION ====="

fresh_db="${prefix}_fresh"
databases+=("$fresh_db")

run_migration "$v001" "$fresh_db"

mongo_eval "$fresh_db" '
  const d = db.getSiblingDB(process.env.TEST_DB);

  if (d.getCollectionNames().includes("user_credentials")) {
    throw new Error("Credentials collection already exists");
  }
'

run_migration "$v002" "$fresh_db"

mongo_eval "$fresh_db" '
  const d = db.getSiblingDB(process.env.TEST_DB);

  if (!d.getCollectionNames().includes("user_credentials")) {
    throw new Error("Credentials collection was not created");
  }

  const indexes = d.user_credentials.getIndexes();

  const idIndex = indexes.find(
    index => index.name === "_id_"
  );

  if (
    !idIndex ||
    idIndex.key._id !== 1 ||
    idIndex.unique !== undefined
  ) {
    throw new Error("Invalid credentials _id index");
  }

  if (d.user_credentials.countDocuments({}) !== 0) {
    throw new Error("New credentials collection is not empty");
  }

  if (
    d.schema_migrations.countDocuments({
      _id: "V002__user_credentials"
    }) !== 1
  ) {
    throw new Error("V002 migration was not registered");
  }

  print("FRESH_COLLECTION=PASS");
'

echo "===== MISSING V001 ====="

missing_v001_db="${prefix}_missing_v001"
databases+=("$missing_v001_db")

expect_v002_failure \
  "$missing_v001_db" \
  "MISSING_V001" \
  "must be applied first"

echo "===== FIRST EXECUTION ====="

main_db="${prefix}_main"
databases+=("$main_db")

run_migration "$v001" "$main_db"

mongo_eval "$main_db" '
  const d = db.getSiblingDB(process.env.TEST_DB);
  const id = "550e8400-e29b-41d4-a716-446655440000";

  d.users.insertOne({
    _id: id,
    name: "Test User",
    email: "credential@example.com",
    roles: ["USER"]
  });

  d.user_credentials.insertOne({
    _id: id,
    passwordHash: "migration-fixture-hash-only"
  });
'

run_migration "$v002" "$main_db"

original_date="$(mongo_eval "$main_db" '
  const d = db.getSiblingDB(process.env.TEST_DB);
  const record = d.schema_migrations.findOne({
    _id: "V002__user_credentials"
  });

  if (!record) {
    throw new Error("V002 migration record missing");
  }

  print(record.appliedAt.toISOString());
')"

echo "===== IDEMPOTENCY AND UNIQUENESS ====="

run_migration "$v002" "$main_db"

ORIGINAL_DATE="$original_date" TEST_DB="$main_db" \
  mongosh "$MONGODB_URI" --quiet --eval '
    const d = db.getSiblingDB(process.env.TEST_DB);
    const id = "550e8400-e29b-41d4-a716-446655440000";

    const index = d.user_credentials.getIndexes().find(
      i => i.name === "_id_"
    );

    if (!index || index.key._id !== 1) {
      throw new Error("MongoDB _id index missing");
    }

    const records = d.schema_migrations.find({
      _id: "V002__user_credentials"
    }).toArray();

    if (
      records.length !== 1 ||
      records[0].appliedAt.toISOString() !==
        process.env.ORIGINAL_DATE
    ) {
      throw new Error("Migration idempotency failed");
    }

    const credential = d.user_credentials.findOne({ _id: id });

    if (
      !credential ||
      credential.passwordHash !== "migration-fixture-hash-only"
    ) {
      throw new Error("Existing credentials were modified");
    }

    print("IDEMPOTENCY=PASS");
    print("EXISTING_CREDENTIAL_PRESERVED=PASS");

    let duplicateRejected = false;

    try {
      d.user_credentials.insertOne({
        _id: id,
        passwordHash: "another-fixture-hash"
      });
    } catch (error) {
      if (error.code !== 11000) throw error;
      duplicateRejected = true;
    }

    if (
      !duplicateRejected ||
      d.user_credentials.countDocuments({ _id: id }) !== 1
    ) {
      throw new Error("Duplicate credential was not rejected");
    }

    print("DUPLICATE_CREDENTIAL=PASS");
  '

echo "===== RECORDED MIGRATION WITHOUT COLLECTION ====="

missing_collection_db="${prefix}_missing_collection"
databases+=("$missing_collection_db")

run_migration "$v001" "$missing_collection_db"

mongo_eval "$missing_collection_db" '
  const d = db.getSiblingDB(process.env.TEST_DB);

  d.schema_migrations.insertOne({
    _id: "V002__user_credentials",
    appliedAt: new Date()
  });
'

expect_v002_failure \
  "$missing_collection_db" \
  "MISSING_COLLECTION" \
  "recorded, but collection is missing"

mongo_eval "$missing_collection_db" '
  const d = db.getSiblingDB(process.env.TEST_DB);

  if (
    d.getCollectionNames().includes("user_credentials") ||
    d.schema_migrations.countDocuments({
      _id: "V002__user_credentials"
    }) !== 1
  ) {
    throw new Error("Failed migration changed database state");
  }

  print("MISSING_COLLECTION_STATE=PASS");
'

test_invalid_credential() {
  local name="$1"
  local document_json="$2"
  local database_name="${prefix}_${name}"

  databases+=("$database_name")

  run_migration "$v001" "$database_name" >/dev/null

  TEST_DB="$database_name" TEST_DOCUMENT="$document_json" \
    mongosh "$MONGODB_URI" --quiet --eval '
      const d = db.getSiblingDB(process.env.TEST_DB);
      const document = JSON.parse(process.env.TEST_DOCUMENT);

      d.user_credentials.insertOne(document);

      if (d.user_credentials.countDocuments({}) !== 1) {
        throw new Error("Invalid fixture was not created");
      }
    ' >/dev/null

  expect_v002_failure \
    "$database_name" \
    "$name" \
    "contains invalid documents"

  mongo_eval "$database_name" '
    const d = db.getSiblingDB(process.env.TEST_DB);

    if (
      d.user_credentials.countDocuments({}) !== 1 ||
      d.schema_migrations.countDocuments({
        _id: "V002__user_credentials"
      }) !== 0
    ) {
      throw new Error("Failed migration changed database state");
    }
  '
}

echo "===== INVALID CREDENTIAL DOCUMENTS ====="

test_invalid_credential \
  "INVALID_UUID" \
  '{"_id":"not-a-uuid","passwordHash":"fixture-hash"}'

test_invalid_credential \
  "WRONG_ID_TYPE" \
  '{"_id":123,"passwordHash":"fixture-hash"}'

test_invalid_credential \
  "MISSING_HASH" \
  '{"_id":"550e8400-e29b-41d4-a716-446655440001"}'

test_invalid_credential \
  "EMPTY_HASH" \
  '{"_id":"550e8400-e29b-41d4-a716-446655440002","passwordHash":""}'

test_invalid_credential \
  "BLANK_HASH" \
  '{"_id":"550e8400-e29b-41d4-a716-446655440003","passwordHash":"   "}'

test_invalid_credential \
  "WRONG_HASH_TYPE" \
  '{"_id":"550e8400-e29b-41d4-a716-446655440004","passwordHash":123}'

echo "V002_AUTOMATED_TEST=PASS"
