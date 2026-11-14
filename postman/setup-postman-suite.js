const fs = require('fs');
const path = require('path');
const execSync = require('child_process').execSync;

const reportPath = path.join(__dirname, 'test-run-report.json');
const testCollectionPath = path.join(__dirname, 'CopMap_API_Tests.postman_collection.json');
const liveDemoCollectionPath = path.join(__dirname, 'CopMap_Live_Demo.postman_collection.json');
const envPath = path.join(__dirname, 'CopMap_Local.postman_environment.json');

const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));

// Fresh 24h JWT tokens generated from live backend
const supervisorToken = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiJlNmQyMTE3My0yNDcwLTQ4MDQtOWY4OS0yM2MyOTFkY2ZkZTkiLCJzdWIiOiJmYzc4NWJkNC0wNzVlLTRmNzktYTZhMi0xYTcyNDA1YmYzY2QiLCJpc3MiOiJhcGkuc3Vic3RyaW5nLmNvbSIsImlhdCI6MTc5NDY4MDYwMCwiZXhwIjoxNzk0NzY3MDAwLCJlbWFpbCI6InN1cGVydmlzb3IudGVzdC4xNzk0Njc3NjEyQHBvbGljZS5nb3YiLCJyb2xlcyI6WyJTdXBlcnZpc29yIl0sInR5cCI6ImFjY2VzcyJ9.NNJJAqza0asA-uefYieNz6kLlSXnhiZeGqiTt8mNOxIJCoE_2JQ_IMSXi-KVYXrZcDNIND9DNEy6Irh7fkiWHg";
const plannerToken = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiJhMTM0NDhkNi0yZmQ4LTQ5ZjEtOWJlNy0zMWU4NGJkZjg4YzIiLCJzdWIiOiJiN2FlMDVhZi04OTY0LTQzNTEtOTM3Ni02MWM2Njg2YzY3ZjciLCJpc3MiOiJhcGkuc3Vic3RyaW5nLmNvbSIsImlhdCI6MTc5NDY4MDYwMSwiZXhwIjoxNzk0NzY3MDAxLCJlbWFpbCI6InBsYW5uZXIudGVzdC4xNzk0Njc3NjEyQHBvbGljZS5nb3YiLCJyb2xlcyI6WyJOT1RfQVNTSUdORUQiXSwidHlwIjoiYWNjZXNzIn0.0cCfcGmTNvigJgzPdyOggX45t6YADq5vESPIMeB8hyVy07hjUNykAA9Lq5tYC1W076pwOprkvvumr_m7Jxr4YQ";
const officerToken = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiJmOWM1YzFkZi0xNmJmLTQ5OTctYTAzOC1jNGY5NDVhMDUyOTEiLCJzdWIiOiJhZGE0MDFmNi1mZDY0LTQyN2ItYmUxYS02NDRhMzFkY2I4Y2MiLCJpc3MiOiJhcGkuc3Vic3RyaW5nLmNvbSIsImlhdCI6MTc5NDY4MDYwMSwiZXhwIjoxNzk0NzY3MDAxLCJlbWFpbCI6Im9mZmljZXIudGVzdC4xNzk0Njc3NjEyQHBvbGljZS5nb3YiLCJyb2xlcyI6WyJOT1RfQVNTSUdORUQiXSwidHlwIjoiYWNjZXNzIn0.KDEbG-DBbt3TLQobzryTBAOWbaDnxy2atYGEPRPu3lFQzud9o1WorS4FDvpU-lz5crsVXxsRJejUaqcVWfMT_A";

const REAL_ENTITIES = {
  baseUrl: "http://localhost:8082",
  teamId: "6881c2dd-237b-474e-8d99-e6f384a8adbf",
  teamId2: "86233c9f-26ea-4a79-858c-65345d164e0b",
  officerId: "dcd3cb4f-ee31-44ee-96d9-f00e43775472",
  plannerOfficerId: "34f2fa84-ff65-414c-8f8a-96f93c9f9853",
  officerUserId: "ada401f6-fd64-427b-be1a-644a31dcb8cc",
  officerMysqlId: "ada401f6-fd64-427b-be1a-644a31dcb8cc",
  operationId: "41c39f43-d780-4483-9817-d7ff73d0f1c5",
  taskId: "8e27d565-8374-4b7c-85ca-5b26d1d7a268",
  assignmentId: "3a10e8ab-4bd1-4351-9a86-14e783697a61",
  approvalRequestId: "2b3a1413-8bc7-4661-8941-3af4c93c119b",
  locationId: "b8cebb22-1df9-41ed-8b49-33b60d7aba1d",
  supervisorToken: supervisorToken,
  plannerToken: plannerToken,
  officerToken: officerToken
};

// 1. Build Saved Demo Example helper
function buildDemoExample(ex) {
  const req = ex.request;
  const res = ex.response;
  const exUrl = req.url;
  const protocol = exUrl.protocol || 'http';
  const host = exUrl.host || ['localhost'];
  const port = exUrl.port || '8082';
  const pathSegments = exUrl.path || [];
  
  let rawUrl = protocol + '://' + host.join('.') + (port ? ':' + port : '') + '/' + pathSegments.join('/');
  const queryList = (exUrl.query || []).map(q => ({ key: q.key, value: q.value }));
  if (queryList.length > 0) {
    rawUrl += '?' + queryList.map(q => q.key + '=' + q.value).join('&');
  }

  const resBody = res.stream ? Buffer.from(res.stream.data).toString('utf8') : '';
  let formattedBody = resBody;
  try {
    if (resBody.trim().startsWith('{') || resBody.trim().startsWith('[')) {
      formattedBody = JSON.stringify(JSON.parse(resBody), null, 2);
    }
  } catch(e) {}

  const originalReqHeader = (req.header || []).map(h => ({
    key: h.key,
    value: h.value,
    type: 'text'
  }));

  const responseHeader = (res.header || []).map(h => ({
    key: h.key,
    value: h.value
  }));

  return {
    name: `Demo Example: ${res.code} ${res.status}`,
    originalRequest: {
      method: req.method,
      header: originalReqHeader,
      url: {
        raw: rawUrl,
        protocol: protocol,
        host: host,
        port: port,
        path: pathSegments,
        query: queryList
      },
      body: req.body ? {
        mode: 'raw',
        raw: req.body.raw || '',
        options: { raw: { language: 'json' } }
      } : undefined
    },
    status: res.status,
    code: res.code,
    _postman_previewlanguage: 'json',
    header: responseHeader.length > 0 ? responseHeader : [{ key: 'Content-Type', value: 'application/json' }],
    cookie: [],
    body: formattedBody
  };
}

// 2. Load Git HEAD collection to get the pristine test definitions
const pristineJson = execSync('git show HEAD:postman/CopMap_API_Tests.postman_collection.json', { cwd: path.join(__dirname, '..') }).toString('utf8');
const testCollection = JSON.parse(pristineJson);

// Ensure runId is dynamic in testCollection bodies so runs are repeatable
let testCollectionStr = JSON.stringify(testCollection);
testCollectionStr = testCollectionStr.replace(/supervisor\.test\.1794677612@police\.gov/g, 'supervisor.test.{{runId}}@police.gov');
testCollectionStr = testCollectionStr.replace(/planner\.test\.1794677612@police\.gov/g, 'planner.test.{{runId}}@police.gov');
testCollectionStr = testCollectionStr.replace(/officer\.test\.1794677612@police\.gov/g, 'officer.test.{{runId}}@police.gov');
testCollectionStr = testCollectionStr.replace(/Alpha Tactical QRT - Sector 7612/g, 'Alpha Tactical QRT - Sector {{runId}}');
testCollectionStr = testCollectionStr.replace(/Bravo Perimeter Security 7612/g, 'Bravo Perimeter Security {{runId}}');
testCollectionStr = testCollectionStr.replace(/Operation Shield Bandobast 7612/g, 'Operation Shield Bandobast {{runId}}');
testCollectionStr = testCollectionStr.replace(/Arterial Nakabandi Checkpoint 7612/g, 'Arterial Nakabandi Checkpoint {{runId}}');

const testCollectionObj = JSON.parse(testCollectionStr);

// Pre-request script to auto-generate fresh runId
testCollectionObj.event = testCollectionObj.event || [];
const autoRunIdScript = [
  "if (!pm.environment.get('runId')) {",
  "    pm.environment.set('runId', Math.floor(Date.now() / 1000).toString().slice(-6));",
  "}"
];
const pIdx = testCollectionObj.event.findIndex(e => e.listen === 'prerequest');
if (pIdx >= 0) {
  testCollectionObj.event[pIdx].script.exec = autoRunIdScript;
} else {
  testCollectionObj.event.push({
    listen: 'prerequest',
    script: { type: 'text/javascript', exec: autoRunIdScript }
  });
}

// Set collection-level fallback variables with real concrete IDs
testCollectionObj.variable = Object.keys(REAL_ENTITIES).map(k => ({
  key: k,
  value: REAL_ENTITIES[k],
  type: "string"
}));

// Attach Saved Demo Examples to each test request
let tIdx = 0;
testCollectionObj.item.forEach(folder => {
  folder.item.forEach(reqItem => {
    const ex = report.run.executions[tIdx];
    if (ex) {
      reqItem.response = [buildDemoExample(ex)];
    }
    tIdx++;
  });
});
fs.writeFileSync(testCollectionPath, JSON.stringify(testCollectionObj, null, 2), 'utf8');
console.log('Saved CopMap_API_Tests.postman_collection.json with dynamic runId + 45 demo examples.');

// 3. Build CopMap_Live_Demo.postman_collection.json
// REAL URLs with concrete IDs, 24h JWT tokens, and 45 demo examples
const liveDemo = JSON.parse(pristineJson);
liveDemo.info.name = "CopMap Police Platform - Real URL Interactive Demo";
liveDemo.info.description = "Complete Interactive Demo Collection where EVERY request URL is the literal REAL URL taking real IDs as concrete examples (e.g. Team ID: 6881c2dd-237b-474e-8d99-e6f384a8adbf, Officer ID: dcd3cb4f-ee31-44ee-96d9-f00e43775472, Operation ID: 41c39f43-d780-4483-9817-d7ff73d0f1c5). Includes full saved Demo Examples displaying formatted response payloads.";

let lIdx = 0;
liveDemo.item.forEach(folder => {
  folder.item.forEach(reqItem => {
    const ex = report.run.executions[lIdx];
    if (!ex) {
      lIdx++;
      return;
    }

    const exUrl = ex.request.url;
    const protocol = exUrl.protocol || 'http';
    const host = exUrl.host || ['localhost'];
    const port = exUrl.port || '8082';
    const pathSegments = exUrl.path || [];
    
    let rawUrl = protocol + '://' + host.join('.') + (port ? ':' + port : '') + '/' + pathSegments.join('/');
    const queryList = (exUrl.query || []).map(q => ({ key: q.key, value: q.value }));
    if (queryList.length > 0) {
      rawUrl += '?' + queryList.map(q => q.key + '=' + q.value).join('&');
    }

    reqItem.request.url = {
      raw: rawUrl,
      protocol: protocol,
      host: host,
      port: port,
      path: pathSegments,
      query: queryList
    };

    // Inject active 24h token
    if (reqItem.request.header) {
      reqItem.request.header.forEach(h => {
        if (h.key === 'Authorization') {
          if (h.value.includes('supervisorToken')) {
            h.value = 'Bearer ' + supervisorToken;
          } else if (h.value.includes('plannerToken')) {
            h.value = 'Bearer ' + plannerToken;
          } else if (h.value.includes('officerToken')) {
            h.value = 'Bearer ' + officerToken;
          }
        }
      });
    }

    // Attach saved Demo Example
    reqItem.response = [buildDemoExample(ex)];

    lIdx++;
  });
});
fs.writeFileSync(liveDemoCollectionPath, JSON.stringify(liveDemo, null, 2), 'utf8');
console.log('Saved CopMap_Live_Demo.postman_collection.json with 100% real URLs and 45 demo examples.');

// 4. Update environment file with concrete values
const envData = {
  id: "9e7d8b1a-beb3-44b2-950c-6cd5bc9d4881",
  name: "CopMap Local Environment",
  values: [
    { key: "baseUrl", value: "http://localhost:8082", enabled: true },
    { key: "runId", value: "", enabled: true },
    ...Object.keys(REAL_ENTITIES).map(k => ({ key: k, value: REAL_ENTITIES[k], enabled: true }))
  ]
};
fs.writeFileSync(envPath, JSON.stringify(envData, null, 2), 'utf8');
console.log('Saved CopMap_Local.postman_environment.json with real entity IDs & 24h JWT tokens.');
