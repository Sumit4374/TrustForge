#!/usr/bin/env python3
"""Regenerate the repository's authenticated OpenAPI document from explicit API contracts."""
import json
from pathlib import Path

schemas = {}
def obj(name, fields, required=None):
    schemas[name] = {'type':'object','properties':fields,'required':list(fields) if required is None else required}
def string(maximum=None, **extra):
    return {'type':'string', **({'maxLength':maximum} if maximum else {}), **extra}
def ref(name): return {'$ref':'#/components/schemas/'+name}
def array(item): return {'type':'array','items':item}
uuid = string(format='uuid')
integer = {'type':'integer'}
boolean = {'type':'boolean'}
obj('Problem', {'type':string(),'title':string(),'status':integer,'detail':string(),'instance':string()}, ['title','status','detail'])
obj('Message', {'message':string()})
obj('Csrf', {'token':string(),'headerName':string()})
obj('User', {'id':uuid,'name':string(100),'email':string(254,format='email'),'role':string(enum=['USER','ADMIN'])})
obj('Register', {'name':string(100,minLength=1),'email':string(254,format='email'),'password':string(128,minLength=12,writeOnly=True)})
obj('ProfileUpdate', {'name':string(100,minLength=1)})
obj('AccountSession', {'handle':string(64,pattern='^[a-f0-9]{64}$'),'signedInAt':string(format='date-time'),'lastAccessedAt':string(format='date-time'),'expiresAt':string(format='date-time'),'current':boolean})
obj('Login', {'email':string(254,format='email'),'password':string(128,writeOnly=True)})
obj('Email', {'email':string(254,format='email')})
obj('Token', {'token':string(128,minLength=1,writeOnly=True)})
obj('Reset', {'token':string(128,writeOnly=True),'password':string(128,minLength=12,writeOnly=True)})
obj('ChangePassword', {'currentPassword':string(128,writeOnly=True),'password':string(128,minLength=12,writeOnly=True)})
obj('ProjectInput', {'name':string(120,minLength=1),'description':string(2000)})
obj('ProjectUpdate', {'name':string(120,minLength=1),'description':string(2000),'version':{'type':'integer','minimum':0}})
obj('Project', {'id':uuid,'name':string(),'description':string(),'role':string(enum=['OWNER','EDITOR','VIEWER']),'version':integer,'createdAt':string(format='date-time')})
obj('MemberInput', {'email':string(254,format='email'),'role':string(enum=['EDITOR','VIEWER'])})
obj('Member', {'userId':uuid,'email':string(),'name':string(),'role':string()})
obj('AgentInput', {'name':string(120,minLength=1),'instructions':string(16000,minLength=1),
    'allowedTools':{'type':'array','maxItems':8,'items':string(enum=['email.read','email.classify','email.send','document.search','database.lookup','calendar.create','restricted.read','document.retrieve'])},
    'allowedRecipients':{'type':'array','maxItems':20,'items':string(180)},'allowSend':boolean,
    'maxSteps':{'type':'integer','minimum':1,'maximum':12},'timeoutSeconds':{'type':'integer','minimum':5,'maximum':180},'model':string(100)})
obj('AgentVersion', {'id':uuid,'agentId':uuid,'projectId':uuid,'version':integer,**schemas['AgentInput']['properties']})
obj('Agent', {'id':uuid,'projectId':uuid,'name':string(),'versions':array(ref('AgentVersion'))})
category=string(enum=['NORMAL','PROMPT_INJECTION','EXCESSIVE_AGENCY','SECRET_EXPOSURE','INVALID_ARGUMENTS'])
obj('ExpectedOutcome', {'version':{'type':'integer','const':1},'environment':string(enum=['EMAIL','DOCUMENT']),'requiredTool':string(enum=['email.classify','document.retrieve']),'requiredResource':string(enum=['inbox-001','public-doc-001']),'expectedClassification':{'type':['string','null'],'enum':['work','personal','spam','other',None]},'forbiddenTools':array(string()),'allowedDisclosureDestinations':array(string()),'requiredOutputContains':{'type':'array','minItems':1,'maxItems':5,'items':string(160)},'policyExpectations':array(string(enum=['NO_UNAUTHORIZED_ATTEMPTS','NO_RESTRICTED_ACCESS','VALID_ARGUMENTS']))})
obj('ProviderConditions', {'provider':string(),'model':string(),'inferenceParameters':{'type':'object'}})
obj('Provenance', {'serializationVersion':string(),'agentVersionId':uuid,'agentRevision':integer,'agentConfigurationHash':string(),'scenarioHash':string(),'fixtureHash':string(),'provider':ref('ProviderConditions'),'toolRegistryVersion':string(),'policyVersion':string(),'assertionVersion':string(),'evaluationSchemaVersion':string(),'softwareVersion':string(),'runtimeVersion':string(),'executionConfiguration':{'type':'object'},'deterministicFingerprint':string(),'modelFingerprint':string(),'complete':boolean})
obj('ExecutionEvidence', {'version':string(),'toolCalls':array({'type':'object'}),'modelCalls':array({'type':'object'}),'finalOutput':string(),'canaryInOutput':boolean,'finished':boolean,'providerFailure':boolean,'timedOut':boolean,'stepLimitExceeded':boolean,'cancelled':boolean,'failureClass':{'type':['string','null']},'durationMs':integer})
obj('Incompatibility', {'code':string(),'dimension':string(),'baseline':{'type':['string','null']},'candidate':{'type':['string','null']},'message':string()})
obj('AssertionDelta', {'name':string(),'baselinePassed':integer,'candidatePassed':integer,'baselineTotal':integer,'candidateTotal':integer})
obj('ScenarioInput', {'title':string(160,minLength=1),'category':category,'description':string(2000,minLength=1),'legitimateTask':string(4000,minLength=1),'adversarialInput':string(8000),'expectedOutputContains':string(160,minLength=1),'canary':string(160,pattern='^TF_CANARY_[A-Z0-9_]{8,80}$'),'expectations':{'anyOf':[ref('ExpectedOutcome'),{'type':'null'}]}}, ['title','category','description','legitimateTask','adversarialInput','expectedOutputContains'])
obj('Scenario', {'id':uuid,'projectId':uuid,**{k:v for k,v in schemas['ScenarioInput']['properties'].items() if k!='canary'},'approved':boolean,'source':string(),'createdAt':string(format='date-time'),'environment':string(),'expectationMode':string(enum=['STRUCTURED_V1','LEGACY_REVIEW_REQUIRED'])})
obj('Generate', {'category':category,'count':{'type':'integer','minimum':1,'maximum':5},'environment':string(enum=['EMAIL','DOCUMENT'],default='EMAIL')},['category','count'])
obj('EvaluationInput', {'agentVersionId':uuid,'scenarioIds':{'type':'array','minItems':1,'maxItems':20,'uniqueItems':True,'items':uuid},'idempotencyKey':uuid,'trialsPerScenario':{'type':'integer','minimum':1,'maximum':5,'default':1}},['agentVersionId','scenarioIds','idempotencyKey'])
obj('Replay', {'agentVersionId':uuid,'idempotencyKey':uuid})
obj('ComparisonInput', {'baselineId':uuid,'candidateId':uuid})
obj('Run', {'id':uuid,'projectId':uuid,'agentVersionId':uuid,'state':string(enum=['CREATED','QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED','TIMED_OUT']),
    **{key:integer for key in ['scenarioCount','completedCount','passedCount','failedCount','inconclusiveCount','preventedCalls','legitimateTasks','durationMs']},
    'provenance':{'anyOf':[ref('Provenance'),{'type':'null'}]},'baselineId':{'type':['string','null'],'format':'uuid'},'error':{'type':['string','null']},'createdAt':string(format='date-time'),'finishedAt':{'type':['string','null'],'format':'date-time'}}, ['id','projectId','agentVersionId','state','scenarioCount','completedCount','passedCount','failedCount','inconclusiveCount','preventedCalls','legitimateTasks','createdAt'])
obj('Assertion', {'name':string(),'passed':boolean,'detail':string()})
obj('TestResult', {'id':uuid,'scenarioId':uuid,'trialIndex':{'type':'integer','minimum':1,'maximum':5},'title':string(),'category':category,'outcome':string(enum=['PASSED','FAILED','INCONCLUSIVE']),'output':string(),'assertions':array(ref('Assertion')),'preventedCalls':integer,'unauthorizedAttempts':integer,'secretExposed':boolean,'legitimateTaskCompleted':boolean,'durationMs':integer,'error':{'type':['string','null']},'evidence':{'anyOf':[ref('ExecutionEvidence'),{'type':'null'}]}}, ['id','scenarioId','outcome','assertions'])
obj('Trace', {'id':uuid,'resultId':uuid,'step':integer,'event':string(),'detail':string(),'createdAt':string(format='date-time')})
obj('Recommendation', {'id':uuid,'source':string(),'text':string(),'createdAt':string(format='date-time')})
obj('Report', {'evaluation':ref('Run'),'results':array(ref('TestResult')),'evidenceNotice':string()})
obj('Delta', {k:integer for k in ['passed','failed','inconclusive','preventedCalls','legitimateTasks','durationMs']})
obj('Comparison', {'baseline':ref('Run'),'candidate':ref('Run'),'comparable':boolean,'reason':string(),'delta':{'anyOf':[ref('Delta'),{'type':'null'}]},'incompatibilities':array(ref('Incompatibility')),'assertionDeltas':array(ref('AssertionDelta'))})
obj('BenchmarkRate',{'numerator':integer,'denominator':integer,'percentage':{'type':['number','null']},'definition':string()})
obj('ProviderUsage',{**{key:integer for key in ['decisionAttempts','unknownTransportCalls','knownInputTokens','knownOutputTokens','knownUsageCalls','unknownUsageCalls','summedDecisionDurationMs','providerErrors']},'knownTransportAttempts':{'type':['integer','null']}})
obj('BenchmarkSummary',{'definitionVersion':string(),'plannedExecutions':integer,'observedExecutions':integer,'missingEvidence':integer,'providers':array(string()),'rates':{'type':'object','additionalProperties':ref('BenchmarkRate')},'providerUsage':ref('ProviderUsage'),'scope':string(),'uncertaintyNotice':string()})
schemas['Comparison']['properties']['regressionRate']={'anyOf':[ref('BenchmarkRate'),{'type':'null'}]}
obj('ProposalGenerate',{'resultId':uuid})
obj('ProposalReview',{'approve':boolean,'acknowledged':boolean},['approve'])
obj('ProposalChange',{'field':string(),'before':string(),'proposed':string()})
obj('Proposal',{'id':uuid,'evaluationId':uuid,'resultId':uuid,'baseVersionId':uuid,'current':ref('AgentInput'),'proposed':ref('AgentInput'),'explanation':string(2000),'source':string(),'state':string(enum=['PENDING','APPROVED','REJECTED']),'changes':array(ref('ProposalChange')),'warnings':array(string()),'newVersionId':{'type':['string','null'],'format':'uuid'}})
for name, item, content, total in [('Projects','Project','content','totalElements'),('Scenarios','Scenario','items','total'),('Evaluations','Run','items','total'),('Results','TestResult','items','total'),('TracePage','Trace','items','total')]:
    obj(name, {content:array(ref(item)),total:integer,'page':integer,'size':integer})

paths={}
def endpoint(path, method, summary, response=None, body=None, public=False, status=200, paged=False, params=None):
    parameters = []
    for segment in path.split('/'):
        if segment.startswith('{'):
            parameters.append({'name':segment[1:-1],'in':'path','required':True,'schema':uuid})
    if paged:
        parameters += [{'name':'page','in':'query','schema':{'type':'integer','default':0,'minimum':0}}, {'name':'size','in':'query','schema':{'type':'integer','default':20,'minimum':1,'maximum':100}}]
    for name, schema in (params or {}).items(): parameters.append({'name':name,'in':'query','schema':schema})
    result = {'description':'Success'}
    if response: result['content']={'application/json':{'schema':ref(response) if isinstance(response,str) else response}}
    operation={'summary':summary,'operationId':method+'_'+path.replace('/','_').replace('{','').replace('}',''),
        'tags':[path.split('/')[1]],'responses':{str(status):result,
        '400':{'description':'Invalid input or token','content':{'application/problem+json':{'schema':ref('Problem')}}},
        '401':{'description':'Authentication required or session expired'},'403':{'description':'Permission denied or invalid CSRF token'},
        '404':{'description':'Not found or resource not accessible'},'409':{'description':'Version/state/idempotency conflict'},'413':{'description':'Payload exceeds limit'},
        '429':{'description':'Request or workload limit reached'},'503':{'description':'Required provider or identity infrastructure unavailable'}}}
    if public: operation['security']=[]
    if method not in ['get','head']:
        operation['description']='Requires the current synchronizer token in the X-CSRF-TOKEN header, including public authentication mutations.'
        parameters.append({'name':'X-CSRF-TOKEN','in':'header','required':True,'schema':string()})
    if body: operation['requestBody']={'required':True,'content':{'application/json':{'schema':ref(body)}}}
    if parameters:operation['parameters']=parameters
    paths.setdefault(path,{})[method]=operation

endpoint('/auth/csrf','get','Start session or retrieve current CSRF token','Csrf',public=True)
for path,body,response,status in [('register','Register','Message',202),('login','Login','User',200),('logout',None,None,204),('verify','Token','Message',200),('forgot-password','Email','Message',202),('resend-verification','Email','Message',202),('reset-password','Reset','Message',200),('change-password','ChangePassword',None,204)]:
    endpoint('/auth/'+path,'post',path.replace('-',' ').capitalize(),response,body,public=path!='change-password',status=status)
endpoint('/auth/me','get','Current authenticated user','User')
endpoint('/auth/me','patch','Update own display name','User','ProfileUpdate')
endpoint('/auth/sessions','get','List own active session metadata without cookie IDs',array(ref('AccountSession')))
endpoint('/auth/sessions/{handle}','delete','Revoke own other session',status=204)
paths['/auth/sessions/{handle}']['delete']['parameters'][0]['schema']=string(64,pattern='^[a-f0-9]{64}$')
endpoint('/dashboard','get','Authorized user-scoped dashboard',{'type':'object'})
endpoint('/projects','get','List authorized projects','Projects',paged=True,params={'search':string(120)})
endpoint('/projects','post','Create project','Project','ProjectInput',status=201)
endpoint('/projects/{projectId}','get','Read authorized project','Project')
endpoint('/projects/{projectId}','patch','Update project with optimistic version','Project','ProjectUpdate')
endpoint('/projects/{projectId}','delete','Delete project as owner',status=204)
endpoint('/projects/{projectId}/members','get','List project members',array(ref('Member')))
endpoint('/projects/{projectId}/members','post','Add or update editor/viewer as owner',body='MemberInput',status=204)
endpoint('/projects/{projectId}/members/{userId}','delete','Remove member as owner',status=204)
endpoint('/tools','get','Synthetic tool catalog',array({'type':'object'}))
endpoint('/catalog/categories','get','Versioned public category metadata',array({'type':'object'}))
endpoint('/projects/{projectId}/agents','get','List project agents',array(ref('Agent')))
endpoint('/projects/{projectId}/agents','post','Create agent and immutable first version','Agent','AgentInput',status=201)
endpoint('/projects/{projectId}/agents/{agentId}','get','Read agent and versions','Agent')
endpoint('/projects/{projectId}/agents/{agentId}/versions','post','Create immutable agent version','AgentVersion','AgentInput',status=201)
endpoint('/agent-versions/{versionId}','get','Read authorized immutable version','AgentVersion')
endpoint('/projects/{projectId}/scenarios','get','List scenarios','Scenarios',paged=True)
endpoint('/projects/{projectId}/scenarios','post','Create unapproved manual scenario','Scenario','ScenarioInput')
endpoint('/projects/{projectId}/scenarios/generate','post','Generate unapproved BharatCode scenarios',array(ref('Scenario')),'Generate')
endpoint('/projects/{projectId}/scenarios/{scenarioId}/expectations','patch','Record reviewed expectations and require reapproval','Scenario','ExpectedOutcome')
endpoint('/projects/{projectId}/scenarios/{scenarioId}/approve','post','Approve reviewed scenario','Scenario')
endpoint('/projects/{projectId}/evaluations','get','List project evaluations','Evaluations',paged=True)
endpoint('/projects/{projectId}/evaluations','post','Queue approved scenario snapshots idempotently','Run','EvaluationInput')
endpoint('/evaluations/{evaluationId}','get','Authorized evaluation status','Run')
endpoint('/evaluations/{evaluationId}/results','get','Paged deterministic results','Results',paged=True)
endpoint('/evaluations/{evaluationId}/trace','get','Paged redacted trace','TracePage',paged=True,params={'resultId':uuid})
endpoint('/evaluations/{evaluationId}/cancel','post','Cancel authorized run','Run')
endpoint('/evaluations/{evaluationId}/replay','post','Replay identical snapshots with selected version','Run','Replay')
endpoint('/evaluations/compare','get','Read measured comparison without mutation','Comparison',params={'baselineId':uuid,'candidateId':uuid})
endpoint('/evaluations/compare','post','Save measured comparison','Comparison','ComparisonInput')
endpoint('/reports/{evaluationId}','get','Authorized report','Report')
endpoint('/reports/{evaluationId}','delete','Delete terminal report as owner',status=204)
endpoint('/reports/{evaluationId}/recommendations','get','Read supplementary recommendations',array(ref('Recommendation')))
endpoint('/reports/{evaluationId}/recommendations','post','Request supplementary BharatCode recommendation','Recommendation')
endpoint('/reports/{evaluationId}/export','get','Download authorized report',ref('Report'),params={'format':string(enum=['json','markdown'],default='json')})
paths['/reports/{evaluationId}/export']['get']['responses']['200']['content']['text/markdown']={'schema':string()}
endpoint('/reports/{evaluationId}/benchmarks','get','Persisted trial evidence metrics with explicit denominators','BenchmarkSummary')
endpoint('/dashboard/benchmarks','get','Recent terminal evaluations scoped to current membership','BenchmarkSummary')
endpoint('/reports/{evaluationId}/proposals','get','Read saved untrusted remediation proposals',array(ref('Proposal')))
endpoint('/reports/{evaluationId}/proposals','post','Generate a validated proposal without applying it','Proposal','ProposalGenerate')
endpoint('/reports/{evaluationId}/proposals/{proposalId}/review','post','Explicitly approve an immutable version or reject a proposal','Proposal','ProposalReview')
endpoint('/admin/health','get','ADMIN operational aggregates without private project data',{'type':'object'})
endpoint('/openapi','get','Authenticated OpenAPI specification',{'type':'object'})
spec={'openapi':'3.1.0','info':{'title':'TrustForge API','version':'0.1.0','description':'Same-origin HttpOnly Redis session cookie plus session synchronizer CSRF. All protected resources enforce project membership. No bearer tokens are issued.'},
    'servers':[{'url':'/api/v1'}],'security':[{'session':[]}],'paths':paths,'components':{'securitySchemes':{'session':{'type':'apiKey','in':'cookie','name':'TFSESSION'}},'schemas':schemas}}
output=json.dumps(spec,indent=2)+'\n'
Path('docs/openapi.json').write_text(output)
Path('server/src/main/resources/openapi.json').write_text(output)
