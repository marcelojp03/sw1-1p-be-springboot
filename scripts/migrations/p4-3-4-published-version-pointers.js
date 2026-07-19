// Dry-run by default. Example:
// mongosh "$MONGODB_URI" --eval "P434_DATABASE='workflow_db'; P434_APPLY=false" --file scripts/migrations/p4-3-4-published-version-pointers.js
// Apply only during a maintenance window after reviewing unresolved policies:
// mongosh "$MONGODB_URI" --eval "P434_DATABASE='workflow_db'; P434_APPLY=true" --file scripts/migrations/p4-3-4-published-version-pointers.js

const applyMigration = typeof P434_APPLY !== "undefined" && P434_APPLY === true;
const expectedDatabase = typeof P434_DATABASE !== "undefined" ? P434_DATABASE : null;

if (!expectedDatabase || db.getName() !== expectedDatabase) {
  throw new Error(`Database mismatch. Expected '${expectedDatabase}', connected to '${db.getName()}'.`);
}

const missingPointerFilter = {
  status: "PUBLISHED",
  $or: [
    { latestPublishedVersionId: { $exists: false } },
    { latestPublishedVersionId: null },
    { latestPublishedVersionId: "" }
  ]
};

const candidates = [];
const unresolved = [];
const cleanupCandidates = [];

db.workflow_policies.find(missingPointerFilter).forEach(policy => {
  const published = db.policy_versions.find({
    policyId: policy._id.toString(),
    status: "PUBLISHED"
  }).sort({ versionNumber: -1 }).toArray();

  if (published.length === 0) {
    unresolved.push({ policyId: policy._id, reason: "No PUBLISHED PolicyVersion exists" });
    return;
  }

  candidates.push({
    policyId: policy._id,
    pointer: published[0]._id,
    versionNumber: published[0].versionNumber,
    versionsToArchive: published.slice(1).map(version => version._id)
  });
});

const inconsistentPointers = [];
db.workflow_policies.find({
  status: "PUBLISHED",
  latestPublishedVersionId: { $exists: true, $nin: [null, ""] }
}).forEach(policy => {
  const version = db.policy_versions.findOne({ _id: policy.latestPublishedVersionId });
  if (!version || version.policyId !== policy._id.toString() || version.status !== "PUBLISHED") {
    inconsistentPointers.push({
      policyId: policy._id,
      pointer: policy.latestPublishedVersionId,
      reason: !version ? "Version missing" : "Version ownership or status mismatch"
    });
    return;
  }
  const extraPublishedVersionIds = db.policy_versions.find({
    policyId: policy._id.toString(),
    status: "PUBLISHED",
    _id: { $ne: policy.latestPublishedVersionId }
  }).map(extra => extra._id).toArray();
  if (extraPublishedVersionIds.length > 0) {
    cleanupCandidates.push({
      policyId: policy._id,
      pointer: policy.latestPublishedVersionId,
      versionsToArchive: extraPublishedVersionIds
    });
  }
});

printjson({
  database: db.getName(),
  applyMigration,
  candidates,
  cleanupCandidates,
  unresolved,
  inconsistentPointers
});

if (!applyMigration) {
  print("DRY-RUN ONLY. Resolve inconsistent pointers before setting P434_APPLY=true.");
} else {
  if (inconsistentPointers.length > 0) {
    throw new Error("Migration blocked: existing inconsistent pointers were detected.");
  }

  const stamp = new Date().toISOString().replace(/[-:.TZ]/g, "");
  const policiesToChange = [...candidates, ...cleanupCandidates];
  const policyIds = policiesToChange.map(candidate => candidate.policyId);
  const versionIds = policiesToChange.flatMap(
    candidate => [candidate.pointer, ...candidate.versionsToArchive]);
  const backups = {
    policies: `backup_p434_workflow_policies_${stamp}`,
    versions: `backup_p434_policy_versions_${stamp}`
  };

  db.workflow_policies.aggregate([
    { $match: { _id: { $in: policyIds } } },
    { $out: backups.policies }
  ]);
  db.policy_versions.aggregate([
    { $match: { _id: { $in: versionIds } } },
    { $out: backups.versions }
  ]);

  candidates.forEach(candidate => {
    db.workflow_policies.updateOne(
      { _id: candidate.policyId, $or: [
        { latestPublishedVersionId: { $exists: false } },
        { latestPublishedVersionId: null },
        { latestPublishedVersionId: "" }
      ] },
      { $set: {
        latestPublishedVersionId: candidate.pointer,
        version: candidate.versionNumber,
        updatedAt: new Date()
      } }
    );
    if (candidate.versionsToArchive.length > 0) {
      db.policy_versions.updateMany(
        { _id: { $in: candidate.versionsToArchive }, status: "PUBLISHED" },
        { $set: { status: "ARCHIVED" } }
      );
    }
  });
  cleanupCandidates.forEach(candidate => {
    db.policy_versions.updateMany(
      { _id: { $in: candidate.versionsToArchive }, status: "PUBLISHED" },
      { $set: { status: "ARCHIVED" } }
    );
  });

  printjson({
    backups,
    migratedPolicies: candidates.length,
    cleanedPolicies: cleanupCandidates.length,
    unresolvedPolicies: unresolved.length,
    remainingMissingPointers: db.workflow_policies.countDocuments(missingPointerFilter)
  });
}
