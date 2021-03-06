package it.cnr.si.flows.ng.listeners.cnr.acquisti;

import it.cnr.si.flows.ng.utils.Enum;
import it.cnr.si.flows.ng.utils.Enum.ProcessDefinitionEnum;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

import static it.cnr.si.flows.ng.utils.Enum.VariableEnum.gruppoRA;
import static it.cnr.si.flows.ng.utils.Utils.PROCESS_VISUALIZER;

@Component
public class AddVisualizerListener implements ExecutionListener {

    private static final long serialVersionUID = 5263454627295290775L;


    private static final Logger LOGGER = LoggerFactory.getLogger(AddVisualizerListener.class);
    private static final String LOG_STRING = "Aggiunta IdentityLink al flusso {} per il gruppo {}";


    @Inject
    private RuntimeService runtimeService;

    @Override
    public void notify(DelegateExecution execution) throws Exception {

        String processDefinitionString = execution.getProcessDefinitionId();
        ProcessDefinitionEnum processDefinition = Enum.ProcessDefinitionEnum.valueOf(processDefinitionString.substring(0, processDefinitionString.indexOf(":")));
        switch (processDefinition) {
            case acquisti:
                String struttura = (String) execution.getVariable(gruppoRA.name());
                struttura = struttura.substring(struttura.indexOf('@') + 1, struttura.length());

                runtimeService.addGroupIdentityLink(execution.getProcessInstanceId(), "ra@" + struttura, PROCESS_VISUALIZER);
                LOGGER.info(LOG_STRING, execution.getId(), String.format("ra@%s", struttura));
                runtimeService.addGroupIdentityLink(execution.getProcessInstanceId(), "direttore@" + struttura, PROCESS_VISUALIZER);
                LOGGER.info(LOG_STRING, execution.getId(), String.format("direttore@%s", struttura));
                runtimeService.addGroupIdentityLink(execution.getProcessInstanceId(), "segreteria@" + struttura, PROCESS_VISUALIZER);
                LOGGER.info(LOG_STRING, execution.getId(), String.format("segreteria@%s", struttura));
                runtimeService.addGroupIdentityLink(execution.getProcessInstanceId(), "rt@" + struttura, PROCESS_VISUALIZER);
                LOGGER.info(LOG_STRING, execution.getId(), String.format("rt@%s", struttura));
                runtimeService.addGroupIdentityLink(execution.getProcessInstanceId(), "sfd@" + struttura, PROCESS_VISUALIZER);
                LOGGER.info(LOG_STRING, execution.getId(), String.format("sfd@%s", struttura));
                break;

            case permessiFerie:

                break;
        }


    }
}
