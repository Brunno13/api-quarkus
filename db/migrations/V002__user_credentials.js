// V002 - Prepare user credentials collection.
// Credentials are linked to users by UUID stored as MongoDB _id.
// MongoDB's built-in _id index guarantees uniqueness.
//
// Run as a controlled migration after V001.
// Never log password hashes or connection credentials.

const migrationId = "V002__user_credentials";
const previousMigrationId = "V001__users_email_unique";
const collectionName = "user_credentials";

const uri = process.env.MONGODB_URI;
const databaseName = process.env.MONGODB_DATABASE;

if (!uri || !databaseName) {
    throw new Error(
        "MONGODB_URI and MONGODB_DATABASE are required"
    );
}

const database = connect(uri).getSiblingDB(databaseName);

const migrations = database.getCollection("schema_migrations");

// Enforce migration ordering.
if (!migrations.findOne({ _id: previousMigrationId })) {
    throw new Error(
        `Migration ${previousMigrationId} must be applied first`
    );
}

if (!database.getCollectionNames().includes("users")) {
    throw new Error("Users collection is missing");
}

const collectionExists = database
    .getCollectionNames()
    .includes(collectionName);

const alreadyApplied = migrations.findOne({
    _id: migrationId
});

if (alreadyApplied && !collectionExists) {
    throw new Error(
        `Migration ${migrationId} recorded, but collection is missing`
    );
}

if (!collectionExists) {
    database.createCollection(collectionName);
}

const credentials = database.getCollection(collectionName);

// Verify the built-in unique _id index.
const idIndex = credentials.getIndexes().find(
    (index) => index.name === "_id_"
);

const validIdIndex =
    idIndex &&
    idIndex.key &&
    idIndex.key._id === 1 &&
    Object.keys(idIndex.key).length === 1;

if (!validIdIndex) {
    throw new Error(
        "Missing or invalid MongoDB _id index"
    );
}

// Domain UUIDs are stored as canonical lowercase strings.
const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

// Validate existing data without exposing password hashes.
const invalidCredential = credentials.findOne({
    $or: [
        { _id: { $not: { $type: "string" } } },
        { _id: { $not: uuidPattern } },
        { passwordHash: { $not: { $type: "string" } } },
        { passwordHash: /^\s*$/ }
    ]
});

if (invalidCredential) {
    throw new Error(
        "User credentials collection contains invalid documents"
    );
}

// Register only after the collection has passed validation.
migrations.updateOne(
    { _id: migrationId },
    { $setOnInsert: { appliedAt: new Date() } },
    { upsert: true }
);

print(`Migration ${migrationId}: OK`);
