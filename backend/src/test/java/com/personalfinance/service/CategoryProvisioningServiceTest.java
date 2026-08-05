package com.personalfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryProvisioningServiceTest {

  @Mock private CategoryRepository categoryRepository;
  @InjectMocks private CategoryProvisioningService service;

  @Test
  void provisionDefaults_creates_the_whole_tree_as_user_owned() {
    User user = User.builder().id(UUID.randomUUID()).build();
    when(categoryRepository.existsByUserId(user.getId())).thenReturn(false);
    when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

    service.provisionDefaults(user);

    int expected =
        DefaultCategories.TREE.size()
            + DefaultCategories.TREE.stream().mapToInt(g -> g.children().size()).sum();
    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository, times(expected)).save(captor.capture());
    // Everything is owned by the user; children carry a parent, top-levels don't.
    assertThat(captor.getAllValues()).allMatch(c -> c.getUser() == user);
    assertThat(captor.getAllValues()).anyMatch(c -> c.getParent() != null);
    assertThat(captor.getAllValues()).anyMatch(c -> c.getParent() == null);
  }

  @Test
  void provisionDefaults_is_a_no_op_when_the_user_already_has_categories() {
    User user = User.builder().id(UUID.randomUUID()).build();
    when(categoryRepository.existsByUserId(user.getId())).thenReturn(true);

    service.provisionDefaults(user);

    verify(categoryRepository, never()).save(any());
  }
}
