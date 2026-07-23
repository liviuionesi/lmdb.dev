import com.filmpire.movie.model.Movie;
import org.springframework.data.mongodb.core.query.Criteria;
public class TestExample {
    public static void main(String[] args) {
        Movie m = new Movie();
        Criteria c = Criteria.byExample(m);
    }
}
