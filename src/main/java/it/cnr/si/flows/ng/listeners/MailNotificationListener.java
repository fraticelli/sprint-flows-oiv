package it.cnr.si.flows.ng.listeners;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.task.IdentityLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.cnr.si.domain.NotificationRule;
import it.cnr.si.flows.ng.service.FlowsMailService;
import it.cnr.si.repository.NotificationRuleRepository;
import it.cnr.si.service.MembershipService;

@Component
public class MailNotificationListener  implements ActivitiEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailNotificationListener.class);
    public static final List<ActivitiEventType> ACCEPTED_EVENTS = Arrays.asList(ActivitiEventType.TASK_CREATED,
            ActivitiEventType.TASK_ASSIGNED,
            ActivitiEventType.TASK_COMPLETED,
            ActivitiEventType.SEQUENCEFLOW_TAKEN,
            ActivitiEventType.PROCESS_STARTED,
            ActivitiEventType.PROCESS_COMPLETED,
            ActivitiEventType.PROCESS_CANCELLED);

    @Inject
    private FlowsMailService mailService;
    @Inject
    private RuntimeService runtimeService;
    @Inject
    private MembershipService membershipService;
    @Inject
    private NotificationRuleRepository notificationService;

    @Override
    public void onEvent(ActivitiEvent event) {
        ActivitiEventType type = event.getType();

        if (type == ActivitiEventType.TASK_CREATED )
            sendStandardCandidateNotification(event);

        if (ACCEPTED_EVENTS.contains(type) )
            sendRuleNotification(event);
    }

    private Map<String, Object> sendStandardCandidateNotification(ActivitiEvent event) {

        String executionId = event.getExecutionId();
        Map<String, Object> variables = runtimeService.getVariables(executionId);

        ActivitiEntityEvent taskEvent = (ActivitiEntityEvent) event;
        TaskEntity task = (TaskEntity) taskEvent.getEntity();
        Set<IdentityLink> candidates = ((TaskEntity)taskEvent.getEntity()).getCandidates();

        candidates.forEach(c -> {
            if (c.getGroupId() != null) {
                List<String> members = membershipService.findMembersInGroup(c.getGroupId());
                members.forEach(m -> {
                    mailService.sendFlowEventNotification(FlowsMailService.TASK_ASSEGNATO_AL_GRUPPO_HTML, variables, task.getName(), m, c.getGroupId());
                });
            }
        });
        return variables;
    }

    private void sendRuleNotification(ActivitiEvent event) {

        ActivitiEventType type = event.getType();
        String executionId = event.getExecutionId();

        Map<String, Object> variables = runtimeService.getVariables(executionId);

        // Notifiche personalizzate
        List<NotificationRule> notificationRules;

        String processDefinitionId = event.getProcessDefinitionId();
        String processDefinitionKey = processDefinitionId.split(":")[0];

        switch (type) {
        case TASK_CREATED:
        case TASK_ASSIGNED:
        case TASK_COMPLETED:
            ActivitiEntityEvent taskEvent = (ActivitiEntityEvent) event;
            TaskEntity task = (TaskEntity) taskEvent.getEntity();
            notificationRules = notificationService.findGroupsByProcessIdEventTypeTaskName(processDefinitionKey, type.toString(), task.getTaskDefinitionKey());
            send(variables, notificationRules, FlowsMailService.TASK_NOTIFICATION, task.getName());
            break;

        case SEQUENCEFLOW_TAKEN:
            notificationRules = notificationService.findGroupsByProcessIdEventType(processDefinitionKey, type.toString());
            send(variables, notificationRules, FlowsMailService.FLOW_NOTIFICATION, null);
            break;

        case PROCESS_STARTED:
        case PROCESS_COMPLETED:
        case PROCESS_CANCELLED:
            notificationRules = notificationService.findGroupsByProcessIdEventType(processDefinitionKey, type.toString());
            send(variables, notificationRules, FlowsMailService.PROCESS_NOTIFICATION, null);
            break;

        default:
            // no action
            break;
        }

    }

    /**
     * Per ogni notification rule invia delle mail
     * Se la notification rule e' riferita a una persona, manda la mail alla persona contenuta nella variabile recipients
     * Se la notification rule e' riferita a un gruppo, manda la mail alle persone member dei gruppi contenti nella variabile recipients
     * @param variables
     * @param notificationRules
     * @param nt
     * @param tn
     */
    private void send(Map<String, Object> variables, List<NotificationRule> notificationRules, String nt, String tn) {

        notificationRules.stream()
        .forEach(rule -> {
            if (rule.isPersona()) {
                Stream.of(rule.getRecipients().split(","))
                .map(s -> s.trim())
                .forEach(personVariableName -> {
                    String person = (String) variables.get(personVariableName);
                    mailService.sendFlowEventNotification(nt, variables, tn, person, null);
                });
            } else {
                Stream.of(rule.getRecipients().split(","))
                .map(s -> s.trim())
                .forEach(groupVariableName -> {
                    String groupName = (String) variables.get(groupVariableName);
                    List<String> members = membershipService.findMembersInGroup(groupName);
                    members.forEach(member -> {
                        mailService.sendFlowEventNotification(nt, variables, tn, member, groupName);
                    });
                });

            }
        });
    }


    @Override
    public boolean isFailOnException() {
        // TODO Auto-generated method stub
        return false;
    }
}