// Dry-run by default. Example:
// mongosh "$MONGODB_URI" --eval "P433_DATABASE='workflow_db'; P433_APPLY=false" --file scripts/migrations/p4-3-3-department-fields.js
// Apply only during a maintenance window after reviewing the dry-run:
// mongosh "$MONGODB_URI" --eval "P433_DATABASE='workflow_db'; P433_APPLY=true" --file scripts/migrations/p4-3-3-department-fields.js

const applyMigration = typeof P433_APPLY !== "undefined" && P433_APPLY === true;
const expectedDatabase = typeof P433_DATABASE !== "undefined" ? P433_DATABASE : null;

if (!expectedDatabase || db.getName() !== expectedDatabase) {
  throw new Error(`Database mismatch. Expected '${expectedDatabase}', connected to '${db.getName()}'.`);
}

const taskFilter = { assignedAreaId: { $exists: true } };
const procedureFilter = { "policySnapshot.nodes.areaId": { $exists: true } };
const policyFilter = {
  $or: [
    { "nodes.areaId": { $exists: true } },
    { "swimlanes.areaId": { $exists: true } }
  ]
};

function aggregateCount(collection, pipeline, field) {
  const result = collection.aggregate([...pipeline, { $count: field }]).toArray();
  return result.length === 0 ? 0 : result[0][field];
}

const counts = {
  tasks: db.tasks.countDocuments(taskFilter),
  procedures: db.procedures.countDocuments(procedureFilter),
  workflowPolicies: db.workflow_policies.countDocuments(policyFilter)
};

const conflicts = {
  tasks: db.tasks.countDocuments({
    assignedAreaId: { $exists: true },
    assignedDepartmentId: { $exists: true }
  }),
  procedureNodes: aggregateCount(db.procedures, [
    { $unwind: "$policySnapshot.nodes" },
    { $match: {
      "policySnapshot.nodes.areaId": { $exists: true },
      "policySnapshot.nodes.departmentId": { $exists: true }
    } }
  ], "procedureNodes"),
  workflowNodes: aggregateCount(db.workflow_policies, [
    { $unwind: "$nodes" },
    { $match: {
      "nodes.areaId": { $exists: true },
      "nodes.departmentId": { $exists: true }
    } }
  ], "workflowNodes"),
  swimlanes: aggregateCount(db.workflow_policies, [
    { $unwind: "$swimlanes" },
    { $match: {
      "swimlanes.areaId": { $exists: true },
      "swimlanes.departmentId": { $exists: true }
    } }
  ], "swimlanes")
};

const malformed = {
  procedureNodes: db.procedures.countDocuments({
    $and: [procedureFilter, { $expr: { $ne: [{ $type: "$policySnapshot.nodes" }, "array"] } }]
  }),
  policyNodes: db.workflow_policies.countDocuments({
    $and: [
      { "nodes.areaId": { $exists: true } },
      { $expr: { $ne: [{ $type: "$nodes" }, "array"] } }
    ]
  }),
  policySwimlanes: db.workflow_policies.countDocuments({
    $and: [
      { "swimlanes.areaId": { $exists: true } },
      { $expr: { $ne: [{ $type: "$swimlanes" }, "array"] } }
    ]
  })
};

printjson({ database: db.getName(), applyMigration, counts, conflicts, malformed });

const unsafeCount = [...Object.values(conflicts), ...Object.values(malformed)]
  .reduce((total, count) => total + count, 0);

if (!applyMigration) {
  print("DRY-RUN ONLY. Set P433_APPLY=true after taking an external backup and stopping application writes.");
} else {
  if (unsafeCount > 0) {
    throw new Error("Migration blocked: destination conflicts or malformed arrays were detected.");
  }

  const stamp = new Date().toISOString().replace(/[-:.TZ]/g, "");
  const backups = {
    tasks: `backup_p433_tasks_${stamp}`,
    procedures: `backup_p433_procedures_${stamp}`,
    policies: `backup_p433_workflow_policies_${stamp}`
  };

  db.tasks.aggregate([{ $match: taskFilter }, { $out: backups.tasks }]);
  db.procedures.aggregate([{ $match: procedureFilter }, { $out: backups.procedures }]);
  db.workflow_policies.aggregate([{ $match: policyFilter }, { $out: backups.policies }]);

  printjson({
    backups,
    backupCounts: {
      tasks: db.getCollection(backups.tasks).countDocuments({}),
      procedures: db.getCollection(backups.procedures).countDocuments({}),
      workflowPolicies: db.getCollection(backups.policies).countDocuments({})
    }
  });

  db.tasks.updateMany(taskFilter, { $rename: { assignedAreaId: "assignedDepartmentId" } });

  db.procedures.find(procedureFilter).forEach(doc => {
    const nodes = doc.policySnapshot.nodes.map(node => {
      if (node && Object.prototype.hasOwnProperty.call(node, "areaId")) {
        node.departmentId = node.areaId;
        delete node.areaId;
      }
      return node;
    });
    db.procedures.updateOne({ _id: doc._id }, { $set: { "policySnapshot.nodes": nodes } });
  });

  db.workflow_policies.find(policyFilter).forEach(doc => {
    const nodes = (Array.isArray(doc.nodes) ? doc.nodes : []).map(node => {
      if (node && Object.prototype.hasOwnProperty.call(node, "areaId")) {
        node.departmentId = node.areaId;
        delete node.areaId;
      }
      return node;
    });
    const swimlanes = (Array.isArray(doc.swimlanes) ? doc.swimlanes : []).map(lane => {
      if (lane && Object.prototype.hasOwnProperty.call(lane, "areaId")) {
        lane.departmentId = lane.areaId;
        delete lane.areaId;
      }
      return lane;
    });
    db.workflow_policies.updateOne({ _id: doc._id }, { $set: { nodes, swimlanes } });
  });

  db.tasks.createIndex(
    { assignedDepartmentId: 1, status: 1 },
    { name: "department_status" }
  );

  printjson({
    remainingLegacy: {
      tasks: db.tasks.countDocuments(taskFilter),
      procedures: db.procedures.countDocuments(procedureFilter),
      workflowPolicies: db.workflow_policies.countDocuments(policyFilter)
    }
  });
}
