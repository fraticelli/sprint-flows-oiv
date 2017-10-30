package it.cnr.si.flows.ng.utils;


public class Enum {

    public enum Actions {

        revoca("Revoca"),
        revocaSemplice("RevocaSemplice"),
        RevocaConProvvedimento("RevocaConProvvedimento");

        private String value;

        private Actions(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }


    public enum Role {
        responsabile("responsabile"),
        responsabileStruttura("responsabile-struttura"),
        supervisore("supervisore"),
        supervisoreStruttura("supervisore-struttura");


        private String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }


    public enum Stato {
        Validato,
        Protocollato,
        Firmato,
        Controfirmato,
        Annullato,
        Pubblicato,
        Sostituito
    }


    public enum Azione {
        Caricamento,
        Aggiornamento,
        Firma,
        Controfirma,
        Pubblicazione,
        Protocollo,
        Validazione,
        Annullo,
        Sostituzione,
        RimozioneDaPubblicazione
    }
}