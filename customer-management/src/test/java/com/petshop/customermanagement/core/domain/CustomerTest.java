package com.petshop.customermanagement.core.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Nested
    class Construction {

        @Test
        void businessConstructorGeneratesIdEnablesAndEmptyPetList() {
            var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");

            assertThat(customer.getId()).isNotNull();
            assertThat(customer.getName()).isEqualTo("Maria");
            assertThat(customer.getCpf()).isEqualTo("12345678900");
            assertThat(customer.getEmail()).isEqualTo("maria@mail.com");
            assertThat(customer.getPhone()).isEqualTo("11999999999");
            assertThat(customer.getEnabled()).isTrue();
            assertThat(customer.getPet()).isNotNull().isEmpty();
        }

        @Test
        void businessConstructorGeneratesDifferentIdsForDifferentInstances() {
            var c1 = new Customer("A", "11111111111", "a@mail.com", "1");
            var c2 = new Customer("B", "22222222222", "b@mail.com", "2");

            assertThat(c1.getId()).isNotEqualTo(c2.getId());
        }

        @Test
        void allArgsConstructorSetsEveryField() {
            var id = UUID.randomUUID();
            var pets = new ArrayList<Pet>();
            var customer = new Customer(id, "Joao", "99988877766", "joao@mail.com", "11888888888", false, pets);

            assertThat(customer.getId()).isEqualTo(id);
            assertThat(customer.getName()).isEqualTo("Joao");
            assertThat(customer.getCpf()).isEqualTo("99988877766");
            assertThat(customer.getEmail()).isEqualTo("joao@mail.com");
            assertThat(customer.getPhone()).isEqualTo("11888888888");
            assertThat(customer.getEnabled()).isFalse();
            assertThat(customer.getPet()).isSameAs(pets);
        }

        @Test
        void noArgsConstructorLeavesFieldsNull() {
            var customer = new Customer();

            assertThat(customer.getId()).isNull();
            assertThat(customer.getName()).isNull();
            assertThat(customer.getPet()).isNull();
        }
    }

    @Nested
    class Update {

        @Test
        void updatesAllFieldsWhenAllPresent() {
            var customer = new Customer("Old Name", "12345678900", "old@mail.com", "111");

            customer.update("New Name", "new@mail.com", "222");

            assertThat(customer.getName()).isEqualTo("New Name");
            assertThat(customer.getEmail()).isEqualTo("new@mail.com");
            assertThat(customer.getPhone()).isEqualTo("222");
        }

        @Test
        void keepsAllFieldsWhenAllNull() {
            var customer = new Customer("Old Name", "12345678900", "old@mail.com", "111");

            customer.update(null, null, null);

            assertThat(customer.getName()).isEqualTo("Old Name");
            assertThat(customer.getEmail()).isEqualTo("old@mail.com");
            assertThat(customer.getPhone()).isEqualTo("111");
        }

        @Test
        void updatesOnlyNameWhenOnlyNameProvided() {
            var customer = new Customer("Old Name", "12345678900", "old@mail.com", "111");

            customer.update("New Name", null, null);

            assertThat(customer.getName()).isEqualTo("New Name");
            assertThat(customer.getEmail()).isEqualTo("old@mail.com");
            assertThat(customer.getPhone()).isEqualTo("111");
        }

        @Test
        void updatesOnlyEmailWhenOnlyEmailProvided() {
            var customer = new Customer("Old Name", "12345678900", "old@mail.com", "111");

            customer.update(null, "new@mail.com", null);

            assertThat(customer.getName()).isEqualTo("Old Name");
            assertThat(customer.getEmail()).isEqualTo("new@mail.com");
            assertThat(customer.getPhone()).isEqualTo("111");
        }

        @Test
        void updatesOnlyPhoneWhenOnlyPhoneProvided() {
            var customer = new Customer("Old Name", "12345678900", "old@mail.com", "111");

            customer.update(null, null, "222");

            assertThat(customer.getName()).isEqualTo("Old Name");
            assertThat(customer.getEmail()).isEqualTo("old@mail.com");
            assertThat(customer.getPhone()).isEqualTo("222");
        }
    }

    @Nested
    class EqualsAndHashCode {

        @Test
        void customersWithSameIdAreEqualEvenWithDifferentOtherFields() {
            var id = UUID.randomUUID();
            var c1 = new Customer(id, "A", "111", "a@mail.com", "1", true, new ArrayList<>());
            var c2 = new Customer(id, "B", "222", "b@mail.com", "2", false, new ArrayList<>());

            assertThat(c1).isEqualTo(c2);
            assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        }

        @Test
        void customersWithDifferentIdsAreNotEqual() {
            var c1 = new Customer("A", "11111111111", "a@mail.com", "1");
            var c2 = new Customer("B", "22222222222", "b@mail.com", "2");

            assertThat(c1).isNotEqualTo(c2);
        }

        @Test
        void isNotEqualToNullOrOtherType() {
            var customer = new Customer("A", "11111111111", "a@mail.com", "1");

            assertThat(customer).isNotEqualTo(null);
            assertThat(customer).isNotEqualTo("not a customer");
        }

        @Test
        void isEqualToItself() {
            var customer = new Customer("A", "11111111111", "a@mail.com", "1");

            assertThat(customer).isEqualTo(customer);
        }
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var customer = new Customer();
        var id = UUID.randomUUID();
        var pets = new ArrayList<Pet>();

        customer.setId(id);
        customer.setName("Name");
        customer.setCpf("cpf");
        customer.setEmail("email");
        customer.setPhone("phone");
        customer.setEnabled(true);
        customer.setPet(pets);

        assertThat(customer.getId()).isEqualTo(id);
        assertThat(customer.getName()).isEqualTo("Name");
        assertThat(customer.getCpf()).isEqualTo("cpf");
        assertThat(customer.getEmail()).isEqualTo("email");
        assertThat(customer.getPhone()).isEqualTo("phone");
        assertThat(customer.getEnabled()).isTrue();
        assertThat(customer.getPet()).isSameAs(pets);
    }
}
