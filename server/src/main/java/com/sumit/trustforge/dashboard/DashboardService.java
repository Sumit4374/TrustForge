package com.sumit.trustforge.dashboard;

import com.sumit.trustforge.ai.AIModelGateway;
import com.sumit.trustforge.common.Actor;
import com.sumit.trustforge.common.ProjectChanged;
import com.sumit.trustforge.infrastructure.ReadCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {
    public record RecentRun(UUID id, UUID projectId, String state, int scenarioCount,
                            int passedCount, int failedCount, int inconclusiveCount, String createdAt) {}
    public record CategoryCount(String category, long passed, long failed, long inconclusive) {}
    public record Dashboard(long projects, long totalEvaluations, long completedEvaluations,
                            long passed, long failed, long inconclusive, long preventedCalls,
                            long legitimateTasks, List<RecentRun> recentRuns,
                            List<CategoryCount> categoryBreakdown, boolean providerConfigured) {}
    public record CachedDashboard(List<String> scope, Dashboard value) {}
    private final JdbcTemplate db;
    private final ReadCache cache;
    private final ObjectMapper json;
    private final AIModelGateway provider;
    public DashboardService(JdbcTemplate db, ReadCache cache, ObjectMapper json, AIModelGateway provider) {
        this.db = db; this.cache = cache; this.json = json; this.provider = provider;
    }
    public Dashboard get() {
        UUID actor = Actor.require();
        // Membership is authoritative even on a cache hit. Revocation changes the scope.
        List<String> scope = db.query("SELECT project_id,role FROM project_members WHERE user_id=? ORDER BY project_id",
                (rs, row) -> rs.getString(1) + ":" + rs.getString(2), actor);
        String key = key(actor);
        String stored = cache.get(key);
        if (stored != null) {
            try {
                CachedDashboard value = json.readValue(stored, CachedDashboard.class);
                if (scope.equals(value.scope()) && provider.configured() == value.value().providerConfigured()) return value.value();
            } catch (RuntimeException invalidCache) { cache.delete(key); }
        }
        Map<String, Object> totals = db.queryForMap("""
            SELECT count(*) AS total, count(*) FILTER(WHERE e.state='COMPLETED') AS completed,
              coalesce(sum(e.passed_count),0) AS passed, coalesce(sum(e.failed_count),0) AS failed,
              coalesce(sum(e.inconclusive_count),0) AS inconclusive,
              coalesce(sum(e.prevented_calls),0) AS prevented, coalesce(sum(e.legitimate_tasks),0) AS legitimate
            FROM evaluation_runs e JOIN project_members m ON m.project_id=e.project_id WHERE m.user_id=?
            """, actor);
        List<RecentRun> recent = db.query("""
            SELECT e.* FROM evaluation_runs e JOIN project_members m ON m.project_id=e.project_id
            WHERE m.user_id=? ORDER BY e.created_at DESC,e.id DESC LIMIT 8
            """, (rs, row) -> new RecentRun(rs.getObject("id", UUID.class),rs.getObject("project_id", UUID.class),
                rs.getString("state"),rs.getInt("scenario_count"),rs.getInt("passed_count"),rs.getInt("failed_count"),
                rs.getInt("inconclusive_count"),rs.getTimestamp("created_at").toInstant().toString()), actor);
        List<CategoryCount> categories = db.query("""
            SELECT r.category, count(*) FILTER(WHERE r.outcome='PASSED') AS passed,
              count(*) FILTER(WHERE r.outcome='FAILED') AS failed, count(*) FILTER(WHERE r.outcome='INCONCLUSIVE') AS inconclusive
            FROM evaluation_test_results r JOIN evaluation_runs e ON e.id=r.evaluation_id
            JOIN project_members m ON m.project_id=e.project_id WHERE m.user_id=? GROUP BY r.category ORDER BY r.category
            """, (rs,row) -> new CategoryCount(rs.getString("category"),rs.getLong("passed"),rs.getLong("failed"),rs.getLong("inconclusive")), actor);
        Dashboard result = new Dashboard(scope.size(), number(totals,"total"),number(totals,"completed"),
            number(totals,"passed"),number(totals,"failed"),number(totals,"inconclusive"),number(totals,"prevented"),
            number(totals,"legitimate"),recent,categories,provider.configured());
        cache.put(key,json.writeValueAsString(new CachedDashboard(scope,result)),Duration.ofSeconds(30));
        return result;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=true)
    public void invalidate(ProjectChanged event) {
        db.query("SELECT user_id FROM project_members WHERE project_id=?", rs -> {
            cache.delete(key(rs.getObject(1,UUID.class)));
        }, event.projectId());
    }
    static String key(UUID userId) { return "tf:ui-cache:v1:dashboard:" + userId; }
    private static long number(Map<String,Object> values, String key) { return ((Number) values.get(key)).longValue(); }
}
