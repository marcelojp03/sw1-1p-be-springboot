// Run once with mongosh against the target database before deploying P4.3.3.
db.tasks.updateMany(
  { assignedAreaId: { $exists: true } },
  { $rename: { assignedAreaId: "assignedDepartmentId" } }
);

db.procedures.find({ "policySnapshot.nodes.areaId": { $exists: true } }).forEach(doc => {
  const nodes = doc.policySnapshot.nodes.map(node => {
    if (Object.prototype.hasOwnProperty.call(node, "areaId")) {
      node.departmentId = node.areaId;
      delete node.areaId;
    }
    return node;
  });
  db.procedures.updateOne({ _id: doc._id }, { $set: { "policySnapshot.nodes": nodes } });
});

db.workflow_policies.find({
  $or: [
    { "nodes.areaId": { $exists: true } },
    { "swimlanes.areaId": { $exists: true } }
  ]
}).forEach(doc => {
  const nodes = (doc.nodes || []).map(node => {
    if (Object.prototype.hasOwnProperty.call(node, "areaId")) {
      node.departmentId = node.areaId;
      delete node.areaId;
    }
    return node;
  });
  const swimlanes = (doc.swimlanes || []).map(lane => {
    if (Object.prototype.hasOwnProperty.call(lane, "areaId")) {
      lane.departmentId = lane.areaId;
      delete lane.areaId;
    }
    return lane;
  });
  db.workflow_policies.updateOne(
    { _id: doc._id },
    { $set: { nodes, swimlanes } }
  );
});
