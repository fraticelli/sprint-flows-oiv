package it.cnr.si.service;

import it.cnr.si.flows.ng.dto.FlowsAttachment;
import it.cnr.si.flows.ng.service.FlowsAttachmentService;
import it.cnr.si.flows.ng.service.FlowsProcessDiagramService;
import it.cnr.si.flows.ng.service.FlowsProcessInstanceService;
import it.cnr.si.flows.ng.utils.Utils;
import org.activiti.engine.RuntimeService;
import org.activiti.rest.service.api.engine.variable.RestVariable;
import org.activiti.rest.service.api.history.HistoricIdentityLinkResponse;
import org.activiti.rest.service.api.history.HistoricProcessInstanceResponse;
import org.activiti.rest.service.api.history.HistoricTaskInstanceResponse;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rst.pdfbox.layout.elements.ControlElement;
import rst.pdfbox.layout.elements.Document;
import rst.pdfbox.layout.elements.ImageElement;
import rst.pdfbox.layout.elements.Paragraph;
import rst.pdfbox.layout.text.BaseFont;
import rst.pdfbox.layout.text.Position;

import javax.inject.Inject;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.*;
import java.util.List;

import static org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD;


/**
 * Created by Paolo on 13/06/17.
 */
@Service
public class SummaryPdfService {

    public static final String TITLE = "title";
    private static final Logger LOGGER = LoggerFactory.getLogger(SummaryPdfService.class);
    private static final float FONT_SIZE = 10;
    private static final float TITLE_SIZE = 18;

    @Inject
    private FlowsProcessInstanceService flowsProcessInstanceService;
    @Inject
    private FlowsProcessDiagramService flowsProcessDiagramService;
    @Inject
    private FlowsAttachmentService flowsAttachmentService;
    @Inject
    private RuntimeService runtimeService;
    private Utils utils = new Utils();




    public String createPdf(String processInstanceId, ByteArrayOutputStream outputStream) throws IOException, ParseException {

        Document pdf = new Document(40, 60, 40, 60);
        Paragraph paragraphField = new Paragraph();
        PDPage pageDiagram = new PDPage();
        Paragraph paragraphDiagram = new Paragraph();
        Paragraph paragraphDocs = new Paragraph();
        Paragraph paragraphHistory = new Paragraph();

        Map<String, Object> map = flowsProcessInstanceService.getProcessInstanceWithDetails(processInstanceId);

        HistoricProcessInstanceResponse processInstances = (HistoricProcessInstanceResponse) map.get("entity");
        String fileName = "Summary_" + processInstances.getBusinessKey() + ".pdf";
        LOGGER.debug("creating pdf {} ", fileName);

        List<RestVariable> variables = processInstances.getVariables();
        ArrayList<Map> tasksSortedList = (ArrayList<Map>) map.get("history");
        Collections.reverse(tasksSortedList);  //ordino i task rispetto alla data di creazione (in senso crescente)

//      genero il titolo del pdf (la bussineskey (es: "Acquisti Trasparenza-2017-1") + titoloIstanzaFlusso (es: "acquisto pc")
        String titolo = processInstances.getBusinessKey() + "\n";
        Optional<RestVariable> variable = variables.stream()
                .filter(a -> (a.getName()).equals("titoloIstanzaFlusso"))
                .findFirst();
        if (variable.isPresent())
            titolo += variable.get().getValue() + "\n\n";
        else {
            variables.stream()
                    .filter(a -> (a.getName()).equals("title"))
                    .findFirst();

            titolo += variable.get().getValue() + "\n\n";
        }
        paragraphField.addText(titolo, TITLE_SIZE, HELVETICA_BOLD);

        for (RestVariable var : variables) {
            switch (var.getName()) {
                case ("initiator"):
                    paragraphField.addText("Avviato da: " + var.getValue() + "\n", FONT_SIZE, HELVETICA_BOLD);
                    break;
                case ("startDate"):
                    if (var.getValue() != null)
                        paragraphField.addText("Avviato il: " + formatDate(utils.format.parse((String) var.getValue())) + "\n", FONT_SIZE, HELVETICA_BOLD);
                    break;
                case ("endDate"):
                    if (var.getValue() != null)
                        paragraphField.addText("Terminato il: " + formatDate(utils.format.parse((String) var.getValue())) + "\n", FONT_SIZE, HELVETICA_BOLD);
                    break;
                case ("gruppoRA"):
                    paragraphField.addText("Gruppo Responsabile Acquisti: " + var.getValue() + "\n", FONT_SIZE, HELVETICA_BOLD);
                    break;
                default:
                    if (var.getValue() != null && !var.getName().equals("titoloIstanzaFlusso"))
                        paragraphField.addText(var.getName() + ": " + var.getValue() + "\n", FONT_SIZE, HELVETICA_BOLD);
            }
        }

        //caricamento diagramma workflow
        ImageElement image = makeDiagram(processInstanceId, paragraphDiagram, pageDiagram);

//        //caricamento documenti allegati al flusso e cronologia
        makeDocs(paragraphDocs, processInstanceId);
//
        //caricamento history del workflow
        makeHistory(paragraphHistory, tasksSortedList);

        pdf.add(paragraphField);
        pdf.add(ControlElement.NEWPAGE);
        pdf.add(paragraphDiagram);
        pdf.add(image);
        pdf.add(ControlElement.NEWPAGE);
        pdf.add(paragraphDocs);
        pdf.add(ControlElement.NEWPAGE);
        pdf.add(paragraphHistory);

        pdf.save(outputStream);

        return fileName;
    }

    private void makeHistory(Paragraph paragraphHistory, ArrayList<Map> tasksSortedList) throws IOException {
        for (Map task : tasksSortedList) {
            HistoricTaskInstanceResponse historyTask = (HistoricTaskInstanceResponse) task.get("historyTask");
            ArrayList<HistoricIdentityLinkResponse> historyIdentityLinks = (ArrayList<HistoricIdentityLinkResponse>) task.get("historyIdentityLink");

            addLine(paragraphHistory, "Titolo task", historyTask.getName(), true, false);
            addLine(paragraphHistory, "Avviato il ", formatDate(historyTask.getStartTime()), true, true);
            if (historyTask.getEndTime() != null)
                addLine(paragraphHistory, "Terminato il ", formatDate(historyTask.getEndTime()), true, true);

            for (HistoricIdentityLinkResponse il : historyIdentityLinks) {
                addLine(paragraphHistory, il.getType(), (il.getUserId() == null ? il.getGroupId() : il.getUserId()), true, true);
            }
            paragraphHistory.addText("\n", FONT_SIZE, HELVETICA_BOLD);
        }
    }


    private String formatDate(Date date) {
        return date != null ? utils.formatoVisualizzazione.format(date) : "";
    }


    private ImageElement makeDiagram(String processInstanceId, Paragraph paragraphDiagram, /*BindingSession adminBindingSession,*/ PDPage page) throws IOException {
        ImageElement image = null;
        intestazione(paragraphDiagram, "Diagramma del flusso:");
        int margineSx = 50;

        InputStream diagram = flowsProcessDiagramService.getDiagramForProcessInstance(processInstanceId, null);

        image = new ImageElement(diagram);
        Dimension scaledDim = getScaledDimension(new Dimension((int) image.getWidth(), (int) image.getHeight()),
                                                 page.getMediaBox().createDimension(), margineSx);
        image.setHeight((float) scaledDim.getHeight());
        image.setWidth((float) scaledDim.getWidth());
        image.setAbsolutePosition(new Position(20, 700));
        return image;
    }


    private void makeDocs(Paragraph paragraphDocs, String processInstancesId /*, String pakageNodeRef, Session adminSession*/) throws IOException {

        intestazione(paragraphDocs, "Documenti del flusso:");
        Map<String, FlowsAttachment> docs = flowsAttachmentService.getAttachementsForProcessInstance(processInstancesId);

        for (Map.Entry<String, FlowsAttachment> entry : docs.entrySet()) {
            FlowsAttachment doc = entry.getValue();
            addLine(paragraphDocs, entry.getKey(), doc.getName(), true, false);

            addLine(paragraphDocs, "Nome del file", doc.getFilename(), true, true);
            addLine(paragraphDocs, "Caricato il", formatDate(doc.getTime()), true, true);
            addLine(paragraphDocs, "Dall'utente", doc.getUsername(), true, true);
            addLine(paragraphDocs, "Nel task", doc.getTaskName(), true, true);
            addLine(paragraphDocs, "Mime-Type", doc.getMimetype(), true, true);
//            todo: far vedere i metadati?
//            addLine(paragraphDocs, "Metadati associati", doc.getMetadati(), true, true);
            paragraphDocs.addText("\n\n", FONT_SIZE, HELVETICA_BOLD);
        }
    }


    private void addLine(Paragraph contentStreamField, String fieldName, String fieldValue, boolean elenco, boolean subField) throws IOException {
        String text = "*" + fieldName + ":* " + fieldValue;
        if (elenco) {
            if (subField)
                contentStreamField.addMarkup(" -+" + text + "\n", FONT_SIZE, BaseFont.Helvetica);
            else
                contentStreamField.addMarkup("-+" + text + "\n", FONT_SIZE, BaseFont.Helvetica);
        } else
            contentStreamField.addText(text + "\n", FONT_SIZE, HELVETICA_BOLD);
    }


    private void intestazione(Paragraph contentStream, String titolo) throws IOException {
        contentStream.addText(titolo + "\n\n", TITLE_SIZE, HELVETICA_BOLD);
    }


    private Dimension getScaledDimension(Dimension imgSize, Dimension boundary, int marginRigth) {
        int originalWidth = imgSize.width;
        int originalHeight = imgSize.height;
        int boundWidth = boundary.width;
        int boundHeight = boundary.height;
        int newWidth = originalWidth;
        int newHeight = originalHeight;

        // controllo se abbiamo bisogno di "scalare" la larghezza
        if (originalWidth > boundWidth) {
            //adatto la larghezza alla pagina ed ai margini
            newWidth = boundWidth - marginRigth;
            //adatto l'altezza per mantenere le proporzioni con la nuova larghezza "scalata"
            newHeight = (newWidth * originalHeight) / originalWidth;
        }

        // verifico se c'è bisogno di scalare anche con la nuova altezza
        if (newHeight > boundHeight) {
            //"scalo" l'altezza
            newHeight = boundHeight;
            //adatto la larghezza per adattarla all'altezza "scalata"
            newWidth = ((newHeight * originalWidth) / originalHeight) - marginRigth;
        }
        return new Dimension(newWidth, newHeight);
    }
}