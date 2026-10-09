// V001 - Create unique index for normalized user emails.
// Run as a controlled migration, before enabling user writes.

const migrationId = "V001__users_email_unique";
const indexName = "ux_users_email";

const uri = process.env.MONGODB_URI;
const databaseName = process.env.MONGODB_DATABASE;

if (!uri || !databaseName) {
  throw new Error("MONGODB_URI and MONGODB_DATABASE are required");
}

const database = connect(uri).getSiblingDB(databaseName);

if (!database.getCollectionNames().includes("users")) {
  database.createCollection("users");
}

const users = database.getCollection("users");
const migrations = database.getCollection("schema_migrations");

const indexes = users.getIndexes();

const indexMatches = (index) =>
  index &&
  index.name === indexName &&
  index.key.email === 1 &&
  Object.keys(index.key).length === 1 &&
  index.unique === true &&
  (!index.collation || index.collation.locale === "simple");

const existing = indexes.find((index) => index.name === indexName);

if (existing && !indexMatches(existing)) {
  throw new Error(`Conflicting index definition: ${indexName}`);
}

const conflictingEmailIndex = indexes.find(
  (index) =>
    index.key.email === 1 &&
    Object.keys(index.key).length === 1 &&
    index.name !== indexName
);

if (conflictingEmailIndex) {
  throw new Error(
    `Conflicting email index: ${conflictingEmailIndex.name}`
  );
}

const alreadyApplied = migrations.findOne({ _id: migrationId });

if (alreadyApplied && !existing) {
  throw new Error(
    `Migration ${migrationId} recorded, but index is missing`
  );
}

if (!existing) {
  // Existing documents must already follow the normalization contract.
  const invalidEmail = users.findOne({
    $or: [
      { email: { $not: { $type: "string" } } },
      { email: "" }
    ]
  });

  if (invalidEmail) {
    throw new Error("Users collection contains invalid emails");
  }

  const notNormalized = users.aggregate([
    {
      $match: {
        $expr: {
          $ne: [
            "$email",
            { $toLower: { $trim: { input: "$email" } } }
          ]
        }
      }
    },
    { $limit: 1 }
  ]).toArray();

  if (notNormalized.length > 0) {
    throw new Error("Users collection contains non-normalized emails");
  }

  users.createIndex(
    { email: 1 },
    {
      name: indexName,
      unique: true,
      collation: { locale: "simple" }
    }
  );
}

const verified = users
  .getIndexes()
  .find((index) => index.name === indexName);

if (!indexMatches(verified)) {
  throw new Error(`Index verification failed: ${indexName}`);
}

migrations.updateOne(
  { _id: migrationId },
  { $setOnInsert: { appliedAt: new Date() } },
  { upsert: true }
);

print(`Migration ${migrationId}: OK`);
