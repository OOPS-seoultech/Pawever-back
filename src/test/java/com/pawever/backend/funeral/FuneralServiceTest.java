package com.pawever.backend.funeral;

import com.pawever.backend.funeral.dto.*;
import com.pawever.backend.funeral.entity.*;
import com.pawever.backend.funeral.repository.*;
import com.pawever.backend.funeral.service.FuneralService;
import com.pawever.backend.global.common.StorageService;

import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FuneralServiceTest {

    @Mock private FuneralCompanyRepository funeralCompanyRepository;
    @Mock private PetFuneralCompanyRepository petFuneralCompanyRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewImageRepository reviewImageRepository;
    @Mock private UserRepository userRepository;
    @Mock private PetRepository petRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private StorageService storageService;

    @InjectMocks private FuneralService funeralService;

    private User user;
    private Pet pet;
    private UserPet userPet;
    private FuneralCompany company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = User.builder().id(1L).build();
        pet = Pet.builder().id(1L).build();
        userPet = UserPet.builder().user(user).pet(pet).isOwner(true).build();
        company = FuneralCompany.builder().id(1L).name("Happy Funeral").latitude(37.0).longitude(127.0).build();
    }

    @Test
    void getFuneralCompanyList_success() {
        when(userPetRepository.findByUserIdAndPetId(user.getId(), pet.getId()))
                .thenReturn(Optional.of(userPet));
        when(funeralCompanyRepository.findAll()).thenReturn(List.of(company));
        when(petFuneralCompanyRepository.findByPetId(pet.getId())).thenReturn(List.of());

        List<FuneralCompanyListResponse> result =
                funeralService.getFuneralCompanyList(user.getId(), pet.getId(), null, null);

        assertEquals(1, result.size());
        assertEquals("Happy Funeral", result.get(0).getName());
    }

    @Test
    void getFuneralCompanyDetail_success() {
        when(userPetRepository.findByUserIdAndPetId(user.getId(), pet.getId()))
                .thenReturn(Optional.of(userPet));
        when(funeralCompanyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(petFuneralCompanyRepository.findByPetIdAndFuneralCompanyId(pet.getId(), company.getId()))
                .thenReturn(Optional.empty());

        FuneralCompanyResponse response = funeralService.getFuneralCompanyDetail(user.getId(), pet.getId(), company.getId());

        assertEquals(company.getName(), response.getName());
        assertNull(response.getUserRegistrationType());
    }

    @Test
    void registerFuneralCompany_newSaved_success() {
        RegisterFuneralCompanyRequest request =
                RegisterFuneralCompanyRequest.builder().petId(pet.getId()).type(RegistrationType.SAVED).build();

        when(userPetRepository.findByUserIdAndPetId(user.getId(), pet.getId()))
                .thenReturn(Optional.of(userPet));
        when(petRepository.findById(pet.getId())).thenReturn(Optional.of(pet));
        when(funeralCompanyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(petFuneralCompanyRepository.findByPetIdAndFuneralCompanyId(pet.getId(), company.getId()))
                .thenReturn(Optional.empty());

        funeralService.registerFuneralCompany(user.getId(), company.getId(), request);

        verify(petFuneralCompanyRepository, times(1)).save(any(PetFuneralCompany.class));
    }

    @Test
    void unregisterFuneralCompany_success() {
        when(userPetRepository.findByUserIdAndPetId(user.getId(), pet.getId()))
                .thenReturn(Optional.of(userPet));

        funeralService.unregisterFuneralCompany(user.getId(), pet.getId(), company.getId());

        verify(petFuneralCompanyRepository, times(1))
                .deleteByPetIdAndFuneralCompanyId(pet.getId(), company.getId());
    }

    @Test
    void createReview_success() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .petId(pet.getId())
                .rating(5)
                .content("Great service")
                .build();

        when(userRepository.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));
        when(funeralCompanyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(petRepository.findById(pet.getId())).thenReturn(Optional.of(pet));
        when(userPetRepository.findByUserIdAndPetId(user.getId(), pet.getId())).thenReturn(Optional.of(userPet));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storageService.upload(any(MultipartFile.class), anyString())).thenReturn("url");
        when(reviewImageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReviewResponse response = funeralService.createReview(user.getId(), company.getId(), request, List.of());

        assertEquals("Great service", response.getContent());
    }

    @Test
    void deleteReview_success() {
        Review review = Review.builder().id(1L).user(user).build();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        funeralService.deleteReview(user.getId(), review.getId());

        verify(reviewRepository, times(1)).delete(review);
    }
}