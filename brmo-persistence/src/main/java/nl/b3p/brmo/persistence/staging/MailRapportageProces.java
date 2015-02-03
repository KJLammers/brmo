/*
 * Copyright (C) 2015 B3Partners B.V.
 */
package nl.b3p.brmo.persistence.staging;

import javax.persistence.Entity;

/**
 * Mail rapportage proces.
 *
 * @author Mark Prins <mark@b3partners.nl>
 */
@Entity
public class MailRapportageProces extends AutomatischProces {

    public static final String DELIM = ",";
    /**
     * de sleutel {@value EMAIL}.
     */
    public static final String EMAIL = "email";
    /**
     * de sleutel {@value PIDS}.
     */
    public static final String PIDS = "pIDS";

    /**
     * haalt de lijst van email adressen op.
     *
     * @return string met adressen
     */
    public String getMailAdressen() {
        return this.getConfig().get(EMAIL);
    }

    /**
     * haalt de lijst van email adressen op.
     *
     * @return array van string met adressen
     */
    public String[] getMailAdressenArray() {
        final String adreslijst = this.getMailAdressen();
        if (adreslijst != null) {
            return adreslijst.split(DELIM);
        } else {
            return null;
        }
    }

    /**
     * wordt gebruikt om opslag van mailadressen te normaliseren.
     *
     * @param adressen een lijst adressen
     */
    public void setMailAdressen(String... adressen) {
        StringBuilder sb = new StringBuilder();
        for (String adres : adressen) {
            sb.append(adres.trim()).append(DELIM);
        }
        sb.setLength(sb.length() - 1);
        this.getConfig().put(EMAIL, sb.toString());
    }

    /**
     * voor stripes formulier input...
     *
     * @param adres een (lijst) adres(sen)
     */
    public void setMailAdressen(String adres) {
        if (adres.contains(DELIM)) {
            this.setMailAdressen(adres.split(DELIM));
        } else {
            this.getConfig().put(EMAIL, adres.trim());
        }
    }
}