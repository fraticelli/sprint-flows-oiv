package it.cnr.si.flows.ng.listeners.oiv.service;

import java.io.IOException;
import java.text.ParseException;

import javax.inject.Inject;

import org.activiti.engine.delegate.DelegateExecution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class ManageSceltaUtente {
	private static final Logger LOGGER = LoggerFactory.getLogger(ManageSceltaUtente.class);


	@Inject
	private CreateOivPdf createOivPdf;

	public void azioneScelta(DelegateExecution execution, String faseEsecuzioneValue, String sceltaUtente) throws IOException, ParseException {
		LOGGER.info("-- azioneScelta: " + faseEsecuzioneValue + " con sceltaUtente: " + sceltaUtente);
		switch(faseEsecuzioneValue){  
		case "istruttoria-end": {
			if(sceltaUtente.equals("richiesta_soccorso_istruttorio")) {
				LOGGER.info("-- faseEsecuzione: " + faseEsecuzioneValue + " con sceltaUtente: " + sceltaUtente);
				execution.setVariable("soccorsoIstruttoriaFlag", "1");
			}
		};break;
		case "valutazione-end": {
			if(sceltaUtente.equals("genera_PDF_preavviso_di_rigetto")) {
				LOGGER.info("-- faseEsecuzione: " + faseEsecuzioneValue + " con sceltaUtente: " + sceltaUtente);
				execution.setVariable("pdfPreavvisoRigettoFlag", "1");
				createOivPdf.CreaPdfOiv(execution, "preavvisoRigetto");
			}
			if(sceltaUtente.equals("richiesta_soccorso_istruttorio")) {
				LOGGER.info("-- faseEsecuzione: " + faseEsecuzioneValue + " con sceltaUtente: " + sceltaUtente);
				execution.setVariable("soccorsoIstruttoriaFlag", "1");
			}
		};break;
		default:  {
			LOGGER.info("--faseEsecuzione: " + faseEsecuzioneValue);
		};break;    
		}
	}
}
