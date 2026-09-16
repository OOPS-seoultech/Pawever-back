package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 한 건에 담긴 아이 한 마리.
 *
 * 한 주문에 두 마리를 넣고 한 마리 값만 받던 일이 있었다. 아이를 줄로 나눠
 * 적어야 몇 마리인지 셀 수 있고, 값도 마리 수대로 매길 수 있다.
 *
 * 사진은 여기에 적지 않는다. {@code goods_survey_photos.pet_index} 가 어느
 * 아이 것인지 가리킨다. 같은 아이디를 두 곳에 적어 두면 한쪽만 고쳤을 때
 * 어긋난다.
 */
@Entity
@Table(
        name = "goods_order_pets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_goods_order_pets_order_index",
                columnNames = {"order_number", "pet_index"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsOrderPet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 20)
    private String orderNumber;

    /** 신청 화면에 적은 순서. 0부터 센다. */
    @Column(name = "pet_index", nullable = false)
    private int petIndex;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String petName;

    /** 키링은 아이마다 따로 고른다. 한 마리만 키링을 붙이는 주문이 있다. */
    @Column(nullable = false)
    private boolean keyringAdded;

    /**
     * 사진 1·2장으로 낼 때 보여 준 안내를 확인했는지.
     *
     * 화면에서만 확인하고 넘어가면 요청을 고쳐 지나갈 수 있어 서버에도 남긴다.
     * 3장 이상이면 안내를 보여 주지 않으므로 거짓으로 남는다.
     */
    @Column(nullable = false)
    private boolean lowPhotoAcknowledged;

    /** 제출 시점의 사진 장수. 나중에 사진이 지워져도 몇 장으로 받았는지는 남는다. */
    @Column(nullable = false)
    private int photoCount;

    public static GoodsOrderPet of(
            String orderNumber,
            int petIndex,
            String petName,
            boolean keyringAdded,
            boolean lowPhotoAcknowledged,
            int photoCount
    ) {
        GoodsOrderPet pet = new GoodsOrderPet();
        pet.orderNumber = orderNumber;
        pet.petIndex = petIndex;
        pet.petName = petName;
        pet.keyringAdded = keyringAdded;
        pet.lowPhotoAcknowledged = lowPhotoAcknowledged;
        pet.photoCount = photoCount;
        return pet;
    }
}
