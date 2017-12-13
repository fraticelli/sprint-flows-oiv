package it.cnr.si.flows.ng.service;

import com.opencsv.CSVWriter;
import it.cnr.si.domain.View;
import it.cnr.si.flows.ng.dto.FlowsAttachment;
import it.cnr.si.flows.ng.exception.ProcessDefinitionAndTaskIdEmptyException;
import it.cnr.si.flows.ng.resource.FlowsAttachmentResource;
import it.cnr.si.flows.ng.utils.Utils;
import it.cnr.si.repository.ViewRepository;
import it.cnr.si.security.FlowsUserDetailsService;
import it.cnr.si.security.PermissionEvaluatorImpl;
import it.cnr.si.security.SecurityUtils;
import it.cnr.si.service.RelationshipService;
import org.activiti.engine.*;
import org.activiti.engine.delegate.BpmnError;
import org.activiti.engine.history.HistoricIdentityLink;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.impl.util.json.JSONArray;
import org.activiti.engine.impl.util.json.JSONObject;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.activiti.rest.common.api.DataResponse;
import org.activiti.rest.service.api.RestResponseFactory;
import org.activiti.rest.service.api.engine.variable.RestVariable;
import org.activiti.rest.service.api.history.HistoricTaskInstanceResponse;
import org.activiti.rest.service.api.runtime.process.ProcessInstanceResponse;
import org.activiti.rest.service.api.runtime.task.TaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static it.cnr.si.flows.ng.utils.Enum.VariableEnum.*;
import static it.cnr.si.flows.ng.utils.Utils.*;


@Service
public class FlowsTaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsTaskService.class);
    private static final String ERROR_MESSAGE = "message";
    @Autowired
    @Qualifier("processEngine")
    protected ProcessEngine engine;
    @Inject
    private HistoryService historyService;
    @Inject
    private RestResponseFactory restResponseFactory;
    @Inject
    private TaskService taskService;
    @Inject
    private AceBridgeService aceBridgeService;
    @Inject
    private FlowsAttachmentResource attachmentResource;
    @Inject
    private FlowsAttachmentService attachmentService;
    @Inject
    private RuntimeService runtimeService;
    @Inject
    private CounterService counterService;
    @Inject
    private RepositoryService repositoryService;
    @Inject
    private FlowsUserDetailsService flowsUserDetailsService;
    @Inject
    private PermissionEvaluatorImpl permissionEvaluator;
    @Inject
    private RelationshipService relationshipService;
    @Inject
    private ViewRepository viewRepository;
    private Utils utils = new Utils();

    // TODO magari un giorno avremo degli array, ma per adesso ce lo facciamo andare bene cosi'
    private static Map<String, Object> extractParameters(MultipartHttpServletRequest req) {

        Map<String, Object> data = new HashMap<>();
        List<String> parameterNames = Collections.list(req.getParameterNames());
        parameterNames.stream().forEach(paramName -> {
            // se ho un json non aggiungo i suoi singoli campi
            if (!parameterNames.contains(paramName.split("\\[")[0] + "_json"))
                data.put(paramName, req.getParameter(paramName));
        });
        return data;
    }

    public DataResponse getMyTask(HttpServletRequest req, String processDefinition, int firstResult, int maxResults, String order) {
        String username = SecurityUtils.getCurrentUserLogin();

        TaskQuery taskQuery = taskService.createTaskQuery()
                .taskAssignee(username)
                .includeProcessVariables();

        if (!processDefinition.equals(ALL_PROCESS_INSTANCES))
            taskQuery.processDefinitionKey(processDefinition);

        taskQuery = (TaskQuery) utils.searchParamsForTasks(req, taskQuery);

        utils.orderTasks(order, taskQuery);

        List<TaskResponse> tasksList = restResponseFactory.createTaskResponseList(taskQuery.listPage(firstResult, maxResults));

        //aggiungo ad ogni singola TaskResponse la variabile che indica se il task è restituibile ad un gruppo (true)
        // o se è stato assegnato ad un utente specifico "dal sistema" (false)
        addIsReleasableVariables(tasksList);

        DataResponse response = new DataResponse();
        response.setStart(firstResult);
        response.setSize(tasksList.size());
        response.setTotal(taskQuery.count());
        response.setData(tasksList);
        return response;
    }

    public Map<String, Object> search(HttpServletRequest req, String processInstanceId, boolean active, String order, int firstResult, int maxResults) {
        Map<String, Object> result = new HashMap<>();
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();

        if (!processInstanceId.equals(ALL_PROCESS_INSTANCES))
            taskQuery.processDefinitionKey(processInstanceId);

        taskQuery = (HistoricTaskInstanceQuery) utils.searchParamsForTasks(req, taskQuery);

        if (active)
            taskQuery.unfinished();
        else
            taskQuery.finished();

        taskQuery = (HistoricTaskInstanceQuery) utils.orderTasks(order, taskQuery);

        long totalItems = taskQuery.count();
        result.put("totalItems", totalItems);

        List<HistoricTaskInstance> taskRaw = taskQuery.includeProcessVariables().listPage(firstResult, maxResults);
        List<HistoricTaskInstanceResponse> tasks = restResponseFactory.createHistoricTaskInstanceResponseList(taskRaw);
        result.put("tasks", tasks);
        return result;
    }

    public DataResponse getAvailableTask(HttpServletRequest req, String processDefinition, int firstResult, int maxResults, String order) {
        String username = SecurityUtils.getCurrentUserLogin();
        List<String> authorities =
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(Utils::removeLeadingRole)
                        .collect(Collectors.toList());

        TaskQuery taskQuery = taskService.createTaskQuery()
                .taskCandidateUser(username)
                .taskCandidateGroupIn(authorities)
                .includeProcessVariables();

        taskQuery = (TaskQuery) utils.searchParamsForTasks(req, taskQuery);

        if (!processDefinition.equals(ALL_PROCESS_INSTANCES))
            taskQuery.processDefinitionKey(processDefinition);

        utils.orderTasks(order, taskQuery);

        List<TaskResponse> list = restResponseFactory.createTaskResponseList(taskQuery.listPage(firstResult, maxResults));

        DataResponse response = new DataResponse();
        response.setStart(firstResult);
        response.setSize(list.size());
        response.setTotal(taskQuery.count());
        response.setData(list);
        return response;
    }

    public DataResponse taskAssignedInMyGroups(HttpServletRequest req, String processDefinition, int firstResult, int maxResults, String order) {
        String username = SecurityUtils.getCurrentUserLogin();

        TaskQuery taskQuery = (TaskQuery) utils.searchParamsForTasks(req, taskService.createTaskQuery().includeProcessVariables());

        if (!processDefinition.equals(ALL_PROCESS_INSTANCES))
            taskQuery.processDefinitionKey(processDefinition);

        utils.orderTasks(order, taskQuery);

        //filtro in ACE gli utenti che appartengono agli stessi gruppi dell'utente loggato
        List<String> myGroups = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(Utils::removeLeadingRole)
                .filter(group -> group.indexOf("afferenza") <= -1)
                .filter(group -> group.indexOf("USER") <= -1)
                .filter(group -> group.indexOf("DEPARTMENT") <= -1)
                .filter(group -> group.indexOf("PREVIUOS") <= -1)
                .collect(Collectors.toList());

////		TODO: da analizzare se le prestazioni sono migliori rispetto a farsi dare la lista di task attivi e ciclare per quali il member è l'assignee (codice di Martin sottostante)
        List<TaskResponse> result = new ArrayList<>();

        List<String> usersInMyGroups = new ArrayList<>();
        for (String myGroup : myGroups) {
            usersInMyGroups.addAll(aceBridgeService.getUsersinAceGroup(myGroup) != null ? aceBridgeService.getUsersinAceGroup(myGroup) : new ArrayList<>());
        }

        usersInMyGroups = usersInMyGroups.stream()
                .distinct()
                .filter(user -> !user.equals(username))
                .collect(Collectors.toList());
//      prendo i task assegnati agli utenti trovati
        for (String user : usersInMyGroups)
            result.addAll(restResponseFactory.createTaskResponseList(taskQuery.taskAssignee(user).list()));

        List<TaskResponse> responseList = result.subList(firstResult <= result.size() ? firstResult : result.size(),
                                                         maxResults <= result.size() ? maxResults : result.size());
        DataResponse response = new DataResponse();
        response.setStart(firstResult);
        response.setSize(responseList.size());
        response.setTotal(result.size());
        response.setData(responseList);
        return response;
    }

    public Map<String, Object> getTask(@PathVariable("id") String taskId) {
        Map<String, Object> response = new HashMap<>();
        Task taskRaw = taskService.createTaskQuery().taskId(taskId).includeProcessVariables().singleResult();

        // task + variables
        TaskResponse task = restResponseFactory.createTaskResponse(taskRaw);
        response.put("task", task);

        // attachments
        ResponseEntity<Map<String, FlowsAttachment>> attachementsEntity = attachmentResource.getAttachementsForTask(taskId);
        Map<String, FlowsAttachment> attachments = attachementsEntity.getBody();
        attachments.values().stream().forEach(e -> e.setBytes(null)); // il contenuto dei file non mi serve, e rallenta l'UI
        response.put("attachments", attachments);
        return response;
    }

    public ResponseEntity<Object> completeTask(MultipartHttpServletRequest req) throws IOException {
        String username = SecurityUtils.getCurrentUserLogin();

        String taskId = (String) req.getParameter("taskId");
        String definitionId = (String) req.getParameter("processDefinitionId");
        if (isEmpty(taskId) && isEmpty(definitionId))
            throw new ProcessDefinitionAndTaskIdEmptyException();

        Map<String, Object> data = extractParameters(req);
        data.putAll(attachmentService.extractAttachmentsVariables(req));

        if (isEmpty(taskId)) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(definitionId).singleResult();
            try {
                String counterId = processDefinition.getName() + "-" + Calendar.getInstance().get(Calendar.YEAR);
                String key = counterId + "-" + counterService.getNext(counterId);

                //recupero l'idStruttura dell'RA che sta avviando il flusso
                List<GrantedAuthority> authorities = relationshipService.getAllGroupsForUser(username);
                List<String> groups = authorities.stream()
                        .map(GrantedAuthority::<String>getAuthority)
                        .map(Utils::removeLeadingRole)
                        .filter(g -> g.startsWith("ra@"))
                        .collect(Collectors.toList());

                if (groups.isEmpty())
                    throw new BpmnError("403", "L'utente non e' abilitato ad avviare questo flusso (NON è Responsabile Acquisti)");
                else if (groups.size() > 1)
                    throw new BpmnError("500", "L'utente appartiene a piu' di un gruppo Responsabile Acquisti");
                else {
                    String gruppoRT = groups.get(0);
                    String idStrutturaString = gruppoRT.substring(gruppoRT.lastIndexOf('@') + 1);

                    data.put(title.name(), key);
                    data.put(initiator.name(), username);
                    data.put(startDate.name(), new Date());

                    ProcessInstance instance = runtimeService.startProcessInstanceById(definitionId, key, data);

                    org.json.JSONObject name = new org.json.JSONObject();
                    name.put(idStruttura.name(), idStrutturaString);
                    name.put(title.name(), data.get(title.name()));
                    name.put(oggetto.name(), data.get(oggetto.name()));
                    name.put(descrizione.name(), data.get(descrizione.name()));
                    name.put(initiator.name(), data.get(initiator.name()));
                    runtimeService.setProcessInstanceName(instance.getId(), name.toString());

                    LOGGER.info("Avviata istanza di processo {}, id: {}", key, instance.getId());

                    ProcessInstanceResponse response = restResponseFactory.createProcessInstanceResponse(instance);
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }
            } catch (Exception e) {
                String errorMessage = String.format("Errore nell'avvio della Process Instances di tipo %s con eccezione:", processDefinition);
                LOGGER.error(errorMessage, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(ERROR_MESSAGE, errorMessage));
            }
        } else {
            try {
                // aggiungo l'identityLink che indica l'utente che esegue il task
                taskService.addUserIdentityLink(taskId, username, TASK_EXECUTOR);
                taskService.setVariablesLocal(taskId, data);
                taskService.complete(taskId, data);

                return new ResponseEntity<>(HttpStatus.OK);
            } catch (Exception e) {
                //Se non riesco a completare il task rimuovo l'identityLink che indica "l'esecutore" del task e restituisco un INTERNAL_SERVER_ERROR
                //l'alternativa sarebbe aggiungere l'identityLink dopo avwer completato il task ma posso creare identityLink SOLO di task attivi
                String errorMessage = String.format("Errore durante il tentativo di completamento del task %s da parte dell'utente %s: %s", taskId, username, e.getMessage());
                LOGGER.error(errorMessage);
                taskService.deleteUserIdentityLink(taskId, username, TASK_EXECUTOR);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(ERROR_MESSAGE, errorMessage));
            }
        }
    }

    public DataResponse getTasksCompletedByMe(HttpServletRequest req, @RequestParam("processDefinition") String processDefinition, @RequestParam("firstResult") int firstResult, @RequestParam("maxResults") int maxResults, @RequestParam("order") String order) {
        String username = SecurityUtils.getCurrentUserLogin();

        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery().taskInvolvedUser(username)
                .includeProcessVariables().includeTaskLocalVariables();

        query = (HistoricTaskInstanceQuery) utils.searchParamsForTasks(req, query);

        if (!processDefinition.equals(ALL_PROCESS_INSTANCES))
            query.processDefinitionKey(processDefinition);

        query = (HistoricTaskInstanceQuery) utils.orderTasks(order, query);
        //seleziono solo i task in cui il TASK_EXECUTOR sia l'utente che sta facendo la richiesta
        List<HistoricTaskInstance> taskList = new ArrayList<>();
        for (HistoricTaskInstance task : query.list()) {
            List<HistoricIdentityLink> identityLinks = historyService.getHistoricIdentityLinksForTask(task.getId());
            for (HistoricIdentityLink hil : identityLinks) {
                if (hil.getType().equals(TASK_EXECUTOR) && hil.getUserId().equals(username))
                    taskList.add(task);
            }
        }
        List<HistoricTaskInstanceResponse> resultList = restResponseFactory.createHistoricTaskInstanceResponseList(
                taskList.subList(firstResult, (firstResult + maxResults <= taskList.size()) ? firstResult + maxResults : taskList.size()));

        DataResponse response = new DataResponse();
        response.setStart(firstResult);
        response.setSize(resultList.size());// numero di task restituiti
        response.setTotal(taskList.size()); //numero totale di task avviati da me
        response.setData(resultList);
        return response;
    }

    private void addIsReleasableVariables(List<TaskResponse> tasks) {
        for (TaskResponse task : tasks) {
            RestVariable isUnclaimableVariable = new RestVariable();
            isUnclaimableVariable.setName("isReleasable");
            // if has candidate groups or users -> can release
            isUnclaimableVariable.setValue(taskService.getIdentityLinksForTask(task.getId())
                                                   .stream()
                                                   .anyMatch(l -> l.getType().equals(IdentityLinkType.CANDIDATE)));
            task.getVariables().add(isUnclaimableVariable);
        }
    }


    //    todo: testare il funzionamento
    public void buildCsv(List<HistoricTaskInstanceResponse> taskInstance, PrintWriter printWriter, String processDefinitionKey) throws IOException {
        // vista (campi e variabili) da inserire nel csv in base alla tipologia di flusso selezionato
        View view = null;
        if (!processDefinitionKey.equals(ALL_PROCESS_INSTANCES)) {
            view = viewRepository.getViewByProcessidType(processDefinitionKey, "export-csv");
        }
        CSVWriter writer = new CSVWriter(printWriter, '\t');
        ArrayList<String[]> entriesIterable = new ArrayList<>();
        boolean hasHeaders = false;
        ArrayList<String> headers = new ArrayList<>();
        headers.add("Business Key");
        headers.add("Start Date");
        for (HistoricTaskInstanceResponse task : taskInstance) {
//            todo: riscrivere perchè adatto alle process instances
            List<RestVariable> variables = task.getVariables();
            ArrayList<String> tupla = new ArrayList<>();
            //field comuni a tutte le Task Instances (name , Start date)
//            tupla.add(task.getBusinessKey());
            tupla.add(task.getName());
            tupla.add(utils.formattaDataOra(task.getStartTime()));

            //field specifici per ogni procesDefinition
            if (view != null) {
                JSONArray fields = new JSONArray(view.getView());
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.getJSONObject(i);
                    tupla.add(Utils.filterProperties(variables, field.getString("varName")));
                    //solo per il primo ciclo, prendo le label dei field specifici
                    if (!hasHeaders)
                        headers.add(field.getString("label"));
                }
            }
            if (!hasHeaders) {
                //inserisco gli headers come intestazione dei field del csv
                entriesIterable.add(0, utils.getArray(headers));
                hasHeaders = true;
            }
            entriesIterable.add(utils.getArray(tupla));
        }
        writer.writeAll(entriesIterable);
        writer.close();
    }
}