package com.petshop.notification_service.core.application.support;

/**
 * Os logs de falha podem acabar em ferramentas de observabilidade; o
 * destinatário só aparece mascarado ("jo***@dominio.com") e o conteúdo do
 * e-mail (que pode trazer o token de reset de senha) nunca é logado.
 */
public final class EmailMasker {

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }
}
