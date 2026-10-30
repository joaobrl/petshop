package com.petshop.commons.exception;

/**
 * Dependência externa (outro microsserviço, por exemplo) indisponível ou inacessível por falha de
 * infraestrutura/configuração — não é regra de negócio. Mapeada para 503 em {@code BaseExceptionHandler},
 * que devolve {@link #getUserMessage()} ao cliente (nunca a mensagem da causa).
 */
public class DependencyUnavailableException extends RuntimeException {

    public static final String DEFAULT_USER_MESSAGE =
            "Um serviço necessário está indisponível no momento. Tente novamente.";

    private final String dependency;
    private final String userMessage;

    public DependencyUnavailableException(String dependency, Throwable cause) {
        this(dependency, DEFAULT_USER_MESSAGE, cause);
    }

    public DependencyUnavailableException(String dependency, String userMessage, Throwable cause) {
        super("Dependência indisponível: " + dependency, cause);
        this.dependency = dependency;
        this.userMessage = userMessage;
    }

    public String getDependency() {
        return dependency;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
