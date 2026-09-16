package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderPet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsOrderPetRepository extends JpaRepository<GoodsOrderPet, Long> {

    List<GoodsOrderPet> findByOrderNumberOrderByPetIndexAsc(String orderNumber);

    List<GoodsOrderPet> findByOrderNumberInOrderByPetIndexAsc(List<String> orderNumbers);
}
