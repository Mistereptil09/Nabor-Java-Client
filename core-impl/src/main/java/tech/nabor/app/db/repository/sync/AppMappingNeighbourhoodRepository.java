package tech.nabor.app.db.repository.sync;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.sync.MappingNeighbourhood;
import tech.nabor.api.repository.sync.MappingNeighbourhoodRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppMappingNeighbourhoodRepository implements MappingNeighbourhoodRepository {

    private final Jdbi jdbi;

    public AppMappingNeighbourhoodRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    private static class Mapper implements RowMapper<MappingNeighbourhood> {
        @Override
        public MappingNeighbourhood map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new MappingNeighbourhood(
                    rs.getString("neighbourhood_id"),
                    rs.getString("neighbourhood_name")
            );
        }
    }

    @Override
    public List<MappingNeighbourhood> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM mapping_neighbourhood_id ORDER BY neighbourhood_name")
                        .map(new Mapper())
                        .list()
        );
    }

    @Override
    public void upsert(String neighbourhoodId, String neighbourhoodName) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO mapping_neighbourhood_id (neighbourhood_id, neighbourhood_name)
                VALUES (:id, :name)
                ON CONFLICT(neighbourhood_id) DO UPDATE SET neighbourhood_name = excluded.neighbourhood_name
                """)
                        .bind("id",   neighbourhoodId)
                        .bind("name", neighbourhoodName)
                        .execute()
        );
    }
}
