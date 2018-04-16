package it.cnr.si.flows.ng.service;

import it.cnr.si.domain.View;
import it.cnr.si.flows.ng.utils.Utils;
import it.cnr.si.repository.ViewRepository;
import org.activiti.rest.service.api.engine.variable.RestVariable;
import org.activiti.rest.service.api.history.HistoricProcessInstanceResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.opencsv.CSVWriter;
import javax.inject.Inject;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.*;

import static it.cnr.si.flows.ng.utils.Utils.ALL_PROCESS_INSTANCES;
import static it.cnr.si.flows.ng.utils.Utils.parseInt;

@Service
public class FlowsCsvService {

    public static final String TITLE = "title";
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsCsvService.class);

    @Inject
    private FlowsProcessInstanceService flowsProcessInstanceService;
    @Inject
    private ViewRepository viewRepository;
    @Inject
    private Utils utils;


	public List<HistoricProcessInstanceResponse> getProcessesStatistics (String processDefinitionKey, String startDateGreat, String startDateLess, boolean activeFlag) throws ParseException {
		Map<String, String> req = new HashMap<>();
		req.put("startDateGreat", startDateGreat);
		req.put("startDateLess", startDateLess);
		req.put(processDefinitionKey, processDefinitionKey);
		String order = "ASC";
		Integer firstResult = -1;
		Integer maxResults = -1;

		Map<String, Object>  flussi = flowsProcessInstanceService.search(req, processDefinitionKey, activeFlag, order, firstResult, maxResults);

		//VALORIZZAZIONE PARAMETRI STATISTICHE
		Integer domandeAttive = parseInt(flussi.get("totalItems").toString());

		LOGGER.debug("nr. domandeAttive: {}", domandeAttive);
		// GESTIONE VARIABILI SINGOLE ISTANZE FLUSSI ATTIVI
		List<HistoricProcessInstanceResponse> activeProcessInstances = (List<HistoricProcessInstanceResponse>) flussi.get("processInstances");
		return activeProcessInstances;
	}



	public void  makeCsv(List<HistoricProcessInstanceResponse> processInstances, PrintWriter printWriter, String processDefinitionKey) throws IOException {
		// vista (campi e variabili) da inserire nel csv in base alla tipologia di flusso selezionato
		View view = null;
		CSVWriter writer = new CSVWriter(printWriter, '\t');
		if (!processDefinitionKey.equals(ALL_PROCESS_INSTANCES)) {
			view = viewRepository.getViewByProcessidType(processDefinitionKey, "export-csv");
			LOGGER.debug("view: {}", view);
		}
		ArrayList<String[]> entriesIterable = new ArrayList<>();
		boolean hasHeaders = false;
		ArrayList<String> headers = new ArrayList<>();
		headers.add("Business Key");
		headers.add("Start Date");
		for (HistoricProcessInstanceResponse pi : processInstances) {
			String processInstanceId = pi.getId();
			Map<String, Object> processInstanceDetails = flowsProcessInstanceService.getProcessInstanceWithDetails(processInstanceId);
			HistoricProcessInstanceResponse processInstance = (HistoricProcessInstanceResponse) processInstanceDetails.get("entity");
			List<RestVariable> variables = processInstance.getVariables();
			
			ArrayList<String> tupla = new ArrayList<>();
			//field comuni a tutte le Process Instances (Business Key, Start date)
			tupla.add(pi.getBusinessKey());
			tupla.add(utils.formattaDataOra(pi.getStartTime()));

			//field specifici per ogni procesDefinition
			if (view != null) {
				try {
					JSONArray fields = new JSONArray(view.getView());
					for (int i = 0; i < fields.length(); i++) {
						JSONObject field = fields.getJSONObject(i);
						tupla.add(Utils.filterProperties(variables, field.getString("varName")));
						//solo per il primo ciclo, prendo le label dei field specifici
						if (!hasHeaders)
							headers.add(field.getString("label"));
					}
				} catch (JSONException e) {
					LOGGER.error("Errore nel processamento del JSON", e);
					throw new IOException(e);
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