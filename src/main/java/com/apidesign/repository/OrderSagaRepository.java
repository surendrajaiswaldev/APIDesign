package com.apidesign.repository;

import com.apidesign.entity.OrderSaga;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, Long> {

    Optional<OrderSaga> findBySagaId(String sagaId);

    List<OrderSaga> findByOrderId(Long orderId);
}
