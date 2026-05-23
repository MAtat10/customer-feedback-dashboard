package com.mahdi.feedback.repository;

import com.mahdi.feedback.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Query("select distinct f.service from Feedback f order by f.service")
    List<String> findDistinctServices();

    @Query(value = """
            select cast(f.timestamp as date) as feedback_day, count(*) as cnt
            from feedback f
            group by cast(f.timestamp as date)
            order by feedback_day
            """, nativeQuery = true)
    List<Object[]> volumePerDay();

    @Query(value = """
            select f.service as service,
                   sum(case when f.sentiment = 'Positive' then 1 else 0 end) as positive,
                   sum(case when f.sentiment = 'Negative' then 1 else 0 end) as negative,
                   sum(case when f.sentiment = 'Neutral'  then 1 else 0 end) as neutral,
                   count(*) as total
            from feedback f
            group by f.service
            order by total desc
            """, nativeQuery = true)
    List<Object[]> volumeByService();
}
