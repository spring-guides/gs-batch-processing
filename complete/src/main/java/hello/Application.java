package hello;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class Application {

	public static void main(String[] args) throws Exception {
		ApplicationContext ctx = new AnnotationConfigApplicationContext(BatchConfiguration.class);
		JobLauncher jobLauncher = ctx.getBean(JobLauncher.class);
		jobLauncher.run(ctx.getBean(Job.class), new JobParametersBuilder().toJobParameters());
		
		JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);
		List<Person> results = jdbcTemplate.query("select first_name, last_name from PEOPLE", new RowMapper<Person>() {
			@Override
			public Person mapRow(final ResultSet rs, int rowNum) throws SQLException {
				return new Person() {{
					setFirstName(rs.getString(1));
					setLastName(rs.getString(2));
				}};
			}
		});
		for (Person person : results) {
			System.out.println("Found <" + person + "> in the database.");
		}
	}
	
}
