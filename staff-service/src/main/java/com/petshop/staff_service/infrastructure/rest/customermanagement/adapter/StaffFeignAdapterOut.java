package com.petshop.staff_service.infrastructure.rest.customermanagement.adapter;

import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.infrastructure.rest.customermanagement.feign.StaffRegistryFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StaffFeignAdapterOut implements StaffPortOut {

    // GET /staff/all agora é paginado; aqui é uma leitura interna (não a
    // paginação de um usuário final), então buscamos o quadro ativo inteiro
    // numa página só — volume de funcionários de um petshop não chega perto
    // desse tamanho.
    private static final int FULL_ROSTER_FETCH_SIZE = 500;

    private final StaffRegistryFeign staffRegistryFeign;

    @Override
    public List<Staff> findAll() {
        return staffRegistryFeign.findAll(0, FULL_ROSTER_FETCH_SIZE).content().stream()
                .map(dto -> new Staff(dto.getId(), dto.getName(), dto.getCpf(), dto.getEmail(), dto.getPhone(), dto.getEnabled(), dto.getRole()))
                .toList();
    }
}
