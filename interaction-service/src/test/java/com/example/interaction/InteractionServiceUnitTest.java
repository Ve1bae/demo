package com.example.interaction;

import com.example.interaction.dto.CreateDynamicRequest;
import com.example.interaction.model.DynamicView;
import com.example.interaction.repository.InteractionRepository;
import com.example.interaction.service.InteractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionServiceUnitTest {

    @Mock
    private InteractionRepository repository;

    @Test
    void createDynamicTrimsContentDeduplicatesMentionsAndSkipsAuthor() {
        InteractionService service = new InteractionService(repository);
        DynamicView expected = new DynamicView(7L, 10L, "今天的直播回放", List.of(11L, 12L), null);
        when(repository.insertDynamic(eq(10L), eq("今天的直播回放"), eq(List.of(11L, 12L))))
                .thenReturn(expected);

        DynamicView result = service.createDynamic(
                10L,
                new CreateDynamicRequest("  今天的直播回放  ", List.of(11L, 10L, 11L, 12L))
        );

        assertEquals(expected, result);
        verify(repository).insertDynamic(10L, "今天的直播回放", List.of(11L, 12L));
    }

    @Test
    void invalidMentionIdIsRejectedBeforeDatabaseWrite() {
        InteractionService service = new InteractionService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.createDynamic(
                10L,
                new CreateDynamicRequest("有效动态", List.of(11L, 0L))
        ));
    }

    @Test
    void invalidUserCannotCreateDynamic() {
        InteractionService service = new InteractionService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.createDynamic(
                null,
                new CreateDynamicRequest("有效动态", List.of())
        ));
    }

    @Test
    void overlongContentIsRejectedInServiceLayer() {
        InteractionService service = new InteractionService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.createDynamic(
                10L,
                new CreateDynamicRequest("x".repeat(1001), List.of())
        ));
    }

    @Test
    void tooManyMentionsAreRejectedInServiceLayer() {
        InteractionService service = new InteractionService(repository);
        List<Long> mentionedUserIds = java.util.stream.LongStream.rangeClosed(20, 40)
                .boxed()
                .toList();

        assertThrows(IllegalArgumentException.class, () -> service.createDynamic(
                10L,
                new CreateDynamicRequest("有效动态", mentionedUserIds)
        ));
    }

    @Test
    void pageParametersAreClampedBeforeRepositoryCall() {
        InteractionService service = new InteractionService(repository);
        when(repository.findDynamics(null, 100, 0)).thenReturn(List.of());

        service.getFeed(1000, -5);

        verify(repository).findDynamics(null, 100, 0);
    }
}
