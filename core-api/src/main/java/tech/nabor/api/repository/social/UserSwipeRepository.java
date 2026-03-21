package tech.nabor.api.repository.social;

import tech.nabor.api.model.enums.SwipeDirection;
import tech.nabor.api.model.social.UserSwipe;

import java.util.List;
import java.util.Optional;

public interface UserSwipeRepository {
    List<UserSwipe> findBySwiperAndDirection(String swiperId, SwipeDirection direction);
    Optional<UserSwipe> findByPair(String swiperId, String swipedId);
    void save(UserSwipe swipe);
}