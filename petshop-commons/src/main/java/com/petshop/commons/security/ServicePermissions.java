package com.petshop.commons.security;

/**
 * Permissões das chamadas serviço-a-serviço (SDD-024 F4). Cada uma é um realm role do
 * Keycloak atribuído só ao service account do serviço chamador (infra/keycloak/realm-petshop.json)
 * e vira {@code GrantedAuthority} com o mesmo nome. Convenção: {@code svc:<destino>:<ação>}.
 */
public final class ServicePermissions {

    /** Prefixo que distingue permissão de serviço de papel de usuário. */
    public static final String PREFIX = "svc:";

    /** booking-service → staff-service GET /api/v1/availability/{general,schedule}. */
    public static final String STAFF_AVAILABILITY_READ = "svc:staff:availability-read";

    /** order-service → customer-management GET /api/v1/customers/find/customer/{id}. */
    public static final String CUSTOMERS_CUSTOMER_READ = "svc:customers:customer-read";

    /** staff-service → customer-management GET /api/v1/staff/all. */
    public static final String CUSTOMERS_STAFF_READ = "svc:customers:staff-read";

    /** order-service → product-registration POST /api/v1/products/{id}/{reserve,release,confirm}. */
    public static final String PRODUCTS_STOCK_WRITE = "svc:products:stock-write";

    private ServicePermissions() {
    }
}
