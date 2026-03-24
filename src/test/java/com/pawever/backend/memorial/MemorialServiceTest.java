package com.pawever.backend.memorial;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.memorial.entity.Comment;
import com.pawever.backend.memorial.entity.ReportReason;
import com.pawever.backend.memorial.repository.*;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import com.pawever.backend.memorial.service.MemorialService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemorialServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentReportRepository commentReportRepository;
    @Mock private PetRepository petRepository;
    @Mock private ReportReasonRepository reportReasonRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private MemorialService memorialService;

    private User user;
    private Pet pet;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = User.builder().id(1L).build();
        pet = Pet.builder()
                .id(1L)
                .lifecycleStatus(LifecycleStatus.BEFORE_FAREWELL)
                .build();
    }

    // =========================
    // 긴급모드
    // =========================

    @Test
    void activateEmergencyMode_success() {
        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByUserIdAndPetId(1L, 1L))
                .thenReturn(Optional.of(UserPet.builder().user(user).pet(pet).build()));

        EmergencyResponse response = memorialService.activateEmergencyMode(1L, 1L);

        assertNotNull(response);
    }

    @Test
    void activateEmergencyMode_alreadyExists() {
        Pet afterPet = Pet.builder()
                .id(1L)
                .lifecycleStatus(LifecycleStatus.AFTER_FAREWELL)
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(afterPet));
        when(userPetRepository.findByUserIdAndPetId(1L, 1L))
                .thenReturn(Optional.of(UserPet.builder().build()));

        assertThrows(CustomException.class,
                () -> memorialService.activateEmergencyMode(1L, 1L));
    }

    // =========================
    // 목록 조회
    // =========================

    @Test
    void getMemorialList_success() {
        Pet recent = Pet.builder()
                .id(1L)
                .lifecycleStatus(LifecycleStatus.AFTER_FAREWELL)
                .deathDate(LocalDateTime.now())
                .build();

        Pet past = Pet.builder()
                .id(2L)
                .lifecycleStatus(LifecycleStatus.AFTER_FAREWELL)
                .deathDate(LocalDateTime.now().minusDays(10))
                .build();

        when(petRepository.findByLifecycleStatus(LifecycleStatus.AFTER_FAREWELL))
                .thenReturn(List.of(recent, past));

        MemorialListResponse response = memorialService.getMemorialList();

        assertEquals(1, response.getRecentMemorials().size());
        assertEquals(1, response.getPastMemorials().size());
    }

    // =========================
    // 상세 조회
    // =========================

    @Test
    void getMemorialDetail_success() {
        pet = Pet.builder()
                .id(1L)
                .lifecycleStatus(LifecycleStatus.AFTER_FAREWELL)
                .build();

        Comment comment = Comment.builder()
                .id(1L)
                .user(user)
                .pet(pet)
                .content("hello")
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByPetId(1L))
                .thenReturn(List.of(UserPet.builder().user(user).pet(pet).build()));
        when(commentRepository.findByPetIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(comment));

        MemorialDetailResponse response =
                memorialService.getMemorialDetail(1L, 1L);

        assertEquals(1, response.getComments().size());
    }

    @Test
    void getMemorialDetail_notMemorial() {
        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

        assertThrows(CustomException.class,
                () -> memorialService.getMemorialDetail(1L, 1L));
    }

    // =========================
    // 댓글
    // =========================

    @Test
    void createComment_success() {
        CommentCreateRequest request = new CommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "test");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CommentResponse response =
                memorialService.createComment(1L, 1L, request);

        assertEquals("test", response.getContent());
    }

    @Test
    void updateComment_success() {
        Comment comment = Comment.builder()
                .id(1L)
                .user(user)
                .content("old")
                .build();

        CommentUpdateRequest request = new CommentUpdateRequest();
        ReflectionTestUtils.setField(request, "content", "new");

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        CommentResponse response =
                memorialService.updateComment(1L, 1L, request);

        assertEquals("new", response.getContent());
    }

    @Test
    void updateComment_forbidden() {
        Comment comment = Comment.builder()
                .id(1L)
                .user(User.builder().id(2L).build())
                .build();

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThrows(CustomException.class,
                () -> memorialService.updateComment(1L, 1L, new CommentUpdateRequest()));
    }

    @Test
    void deleteComment_success_owner() {
        Comment comment = Comment.builder()
                .id(1L)
                .user(user)
                .pet(pet)
                .build();

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        memorialService.deleteComment(1L, 1L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_forbidden() {
        Comment comment = Comment.builder()
                .id(1L)
                .user(User.builder().id(2L).build())
                .pet(pet)
                .build();

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(userPetRepository.existsByUserIdAndPetId(1L, 1L)).thenReturn(false);

        assertThrows(CustomException.class,
                () -> memorialService.deleteComment(1L, 1L));
    }

    // =========================
    // 신고
    // =========================

    @Test
    void reportComment_success() {
        Comment comment = Comment.builder().id(1L).build();

        ReportReason reason = ReportReason.builder().id(1L).build();

        CommentReportRequest request = new CommentReportRequest();
        ReflectionTestUtils.setField(request, "reasonIds", List.of(1L));

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(reportReasonRepository.findAllById(List.of(1L))).thenReturn(List.of(reason));

        memorialService.reportComment(1L, 1L, request);

        verify(commentReportRepository).save(any());
    }

    @Test
    void reportComment_noReason() {
        CommentReportRequest request = new CommentReportRequest();

        when(commentRepository.findById(1L))
                .thenReturn(Optional.of(Comment.builder().build()));
        when(userRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        assertThrows(CustomException.class,
                () -> memorialService.reportComment(1L, 1L, request));
    }
}