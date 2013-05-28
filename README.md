# Getting Started: Batch-based Processing

What you'll build
-----------------

This guide will take you through creating a very basic batch-driven solution. We'll build a service that imports data from a CSV spreadsheet, transforms it with some custom code, and stores the final results in a database.

What you'll need
----------------

- About 15 minutes
- A favorite text editor or IDE
- [JDK 7][jdk7] or later
- Your choice of Maven (3.0+) or Gradle (1.5+)

{!snippet:how-to-complete-this-guide}

<a name="scratch"></a>
Set up the project
------------------
{!snippet:build-system-intro}

<span class="maven">
### Create a Maven POM

`pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>org.springframework</groupId>
	<artifactId>gs-batch-processing-complete</artifactId>
	<version>0.1.0</version>

    <parent>
        <groupId>org.springframework.bootstrap</groupId>
        <artifactId>spring-bootstrap-starters</artifactId>
        <version>0.5.0.BUILD-SNAPSHOT</version>
    </parent>
    
	<dependencies>
        <dependency>
            <groupId>org.springframework.bootstrap</groupId>
            <artifactId>spring-bootstrap-batch-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.hsqldb</groupId>
            <artifactId>hsqldb</artifactId>
        </dependency>
   	</dependencies>

	<repositories>
		<repository>
			<id>spring-snapshots</id>
			<url>http://repo.springsource.org/snapshot</url>
			<snapshots><enabled>true</enabled></snapshots>
		</repository>
		<repository>
			<id>spring-milestones</id>
			<url>http://repo.springsource.org/milestone</url>
			<snapshots><enabled>true</enabled></snapshots>
		</repository>
	</repositories>
	<pluginRepositories>
		<pluginRepository>
			<id>spring-snapshots</id>
			<url>http://repo.springsource.org/snapshot</url>
			<snapshots><enabled>true</enabled></snapshots>
		</pluginRepository>
	</pluginRepositories>
</project>
```
{!snippet:bootstrap-starter-pom-disclaimer}
</span>

<span class="gradle">
### Create a Gradle build script
`build.gradle`
```groovy
TODO: paste complete build.gradle
copile "org.springframework.bootstrap:spring-bootstrap-batch-starter:0.0.1-SNAPSHOT"
compile "org.hsqldb:hsqldb:x.y.z"
```
</span>

Create some business data
--------------------------

Before we can ingest a CSV spreadsheet, we need to create one. Normally this would be data supplied by your customer or a business analyst. In this case, we'll make up our own.

`src/main/resources/sample-data.csv`
```text
Jill,Doe
Joe,Doe
Justin,Doe
Jane,Doe
John,Doe
```

This spreadsheet contains a first name and a last name on each row, separated by a comma. This is a fairly common pattern and something we'll see soon that Spring handles out of the box.

Defining the destination for our data
-------------------------------------

Now that we have an idea what the data looks like, let's write a SQL script to create a table to store it.

`src/main/resources/schema.sql`
```sql
DROP TABLE people IF EXISTS;

CREATE TABLE people  (
	person_id BIGINT IDENTITY NOT NULL PRIMARY KEY,
	first_name VARCHAR(20),
	last_name VARCHAR(20)
);
```

<a name="initial"></a>
Create a business class
-----------------------

Now that we see the format of inputs and outputs for our data, let's write some code to represent a row of data.

`src/main/java/hello/Person.java`
```java
package hello;

public class Person {
    private String lastName;
    private String firstName;
    
    public Person() {
    	
    }

	public Person(String firstName, String lastName) {
    	this.firstName = firstName;
    	this.lastName = lastName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
		this.lastName = lastName;
	}

    @Override
    public String toString() {
        return "firstName: " + firstName + ", lastName: " + lastName;
    }
}
```

The `Person` class can either be instantiated with first and last name through a constructor or by setting the properties.

Create an intermediate processor
--------------------------------

A common paradigm in batch processing is to ingest data, transform it, and then pipe it out somewhere else. Let's write a simple transformer that converts the names to uppercase.

`src/main/java/hello/PersonItemProcessor.java`
```java
package hello;

import org.springframework.batch.item.ItemProcessor;

public class PersonItemProcessor implements ItemProcessor<Person, Person> {
    @Override
    public Person process(final Person person) throws Exception {
        final String firstName = person.getFirstName().toUpperCase();
        final String lastName = person.getLastName().toUpperCase();
        
        final Person transformedPerson = new Person(firstName, lastName);

        System.out.println("Converting (" + person + ") into (" + transformedPerson + ")");
        
        return transformedPerson;
    }
}
```

`PersonItemProcessor` implements Spring Batch's `ItemProcessor` interface. This makes it easy to wire the code into a batch job we'll define further down in this guide. According to the interface, we will get handed an incoming `Person` object, after which we will transform it to an upper-cased `Person`.

> There is no requirement that the input and output types be the same. In fact, it is often the case that after reading one source of data, and different data type is what's needing in the application's data flow.

Putting together a batch job
----------------------------

Now let's put together the actual batch job. Spring Batch provides many utility classes that reduces our need to write custom code. Instead, we can focus on the business logic.

`src/main/java/hello/BatchConfiguration`
```java
package hello;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.bootstrap.SpringApplication;
import org.springframework.bootstrap.context.annotation.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@Configuration
@EnableBatchProcessing
@EnableAutoConfiguration
public class BatchConfiguration {

	@Bean
	public ItemReader<Person> reader() {
		FlatFileItemReader<Person> reader = new FlatFileItemReader<Person>();
		reader.setResource(new ClassPathResource("sample-data.csv"));
		reader.setLineMapper(new DefaultLineMapper<Person>() {{
			setLineTokenizer(new DelimitedLineTokenizer() {{
				setNames(new String[] { "firstName", "lastName" });
			}});
			setFieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
				setTargetType(Person.class);
			}});
		}});
		return reader;
	}

	@Bean
	public ItemProcessor<Person, Person> processor() {
		return new PersonItemProcessor();
	}

	@Bean
	public ItemWriter<Person> writer(DataSource dataSource) {
		JdbcBatchItemWriter<Person> writer = new JdbcBatchItemWriter<Person>();
		writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<Person>());
		writer.setSql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)");
		writer.setDataSource(dataSource);
		return writer;
	}

	@Bean
	public Job importUserJob(JobBuilderFactory jobs, Step s1) {
		return jobs.get("importUserJob")
				.incrementer(new RunIdIncrementer())
				.flow(s1)
				.end()
				.build();
	}

	@Bean
	public Step step1(StepBuilderFactory stepBuilderFactory, ItemReader<Person> reader,
			ItemWriter<Person> writer, ItemProcessor<Person, Person> processor) {
		return stepBuilderFactory.get("step1")
				.<Person, Person> chunk(10)
				.reader(reader)
				.processor(processor)
				.writer(writer)
				.build();
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(BatchConfiguration.class, args);
		List<Person> results = ctx.getBean(JdbcTemplate.class).query("SELECT first_name, last_name FROM people", new RowMapper<Person>() {
			@Override
			public Person mapRow(ResultSet rs, int row) throws SQLException {
				return new Person(rs.getString(1), rs.getString(2));
			}
		});
		for (Person person : results) {
			System.out.println("Found <" + person + "> in the database.");
		}
	}

}
```

For starters, the `@EnableBatchProcessing` annotation adds many critical beans that support jobs, saving us a lot of leg work.

Let's break this down:

```java
	@Bean
	public ItemReader<Person> reader() {
		FlatFileItemReader<Person> reader = new FlatFileItemReader<Person>();
		reader.setResource(new ClassPathResource("sample-data.csv"));
		reader.setLineMapper(new DefaultLineMapper<Person>() {{
			setLineTokenizer(new DelimitedLineTokenizer() {{
				setNames(new String[] { "firstName", "lastName" });
			}});
			setFieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
				setTargetType(Person.class);
			}});
		}});
		return reader;
	}

	@Bean
	public ItemProcessor<Person, Person> processor() {
		return new PersonItemProcessor();
	}

	@Bean
	public ItemWriter<Person> writer(DataSource dataSource) {
		JdbcBatchItemWriter<Person> writer = new JdbcBatchItemWriter<Person>();
		writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<Person>());
		writer.setSql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)");
		writer.setDataSource(dataSource);
		return writer;
	}
```

This first chunk of code defines the input, processor, and output.
- `reader()` creates an `ItemReader`. It looks for a file called `sample-data.csv` and parses each line item with enough information to turn it into a `Person`.
- `processor()` creates an instance of our `PersonItemProcessor` we defined earlier, meant to uppercase the data.
- `write(DataSource)` creates an `ItemWriter`. This one is aimed at a JDBC destination and automatically gets a copy of the dataSource created by `@EnableBatchProcessing`. It includes the SQL statement needed to insert a single `Person` driven by java bean properties.

The next chunk is focused on the actual job configuration.

```java
	@Bean
	public Job importUserJob(JobBuilderFactory jobs, Step s1) {
		return jobs.get("importUserJob")
				.incrementer(new RunIdIncrementer())
				.flow(s1)
				.end()
				.build();
	}

	@Bean
	public Step step1(StepBuilderFactory stepBuilderFactory, ItemReader<Person> reader,
			ItemWriter<Person> writer, ItemProcessor<Person, Person> processor) {
		return stepBuilderFactory.get("step1")
				.<Person, Person> chunk(10)
				.reader(reader)
				.processor(processor)
				.writer(writer)
				.build();
	}
```

The first method defines our job and the second one defines a single step. Jobs are built out of steps, where each step can involved a reader, a processor, and a writer. 

In our job definition, we need an incrementer because jobs use a database to maintain execution state. We then list each of the steps, of which our job has just one step. Finally, the job has an end. With all this, the java API spits out a perfectly configured job.

In our step definition, we define how much data to write at a time. In this case, it writes up to ten records at a time. Next, we configure the reader, processor, and writer using the injected bits from earlier. Finally, the builder API turns it into a nicely built step.

> chunk() is prefixed `<Person,Person>` because it's a generic method. This represents the input and output types of each "chunk" of processing, and lines up with `ItemReader<Person>` and `ItemWriter<Person>`.

Finally, we need the part that runs our application.

```java
	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(BatchConfiguration.class, args);
		List<Person> results = ctx.getBean(JdbcTemplate.class).query("SELECT first_name, last_name FROM people", new RowMapper<Person>() {
			@Override
			public Person mapRow(ResultSet rs, int row) throws SQLException {
				return new Person(rs.getString(1), rs.getString(2));
			}
		});
		for (Person person : results) {
			System.out.println("Found <" + person + "> in the database.");
		}
	}
```

This example uses a memory-based database (provided by `@EnableBatchProcessing`), meaning that when it's all done, the data will be gone. For demonstration purposes, there is a little extra code to create a `JdbcTemplate` and query the database, printing out all the people our batch job inserts.

Build an executable JAR
-----------------------
<span class="maven">
Add the following to your `pom.xml` file (keeping existing properties and plugins intact):

`pom.xml`
```xml
<properties>
	<start-class>hello.BatchConfiguration</start-class>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```
</span>
<span class="gradle">
```groovy
TODO: gradle syntax
```
</span>

The following will produce a single executable JAR file containing all necessary dependency classes:
<span class="maven">
```
$ mvn package
```
</span>
<span class="gradle">
```
$ gradle build
```
</span>

Run the batch job
-----------------

Now you can run it from the jar as well, and distribute that as an executable artifact:
```
$ java -jar target/gs-batch-processing-complete-1.0.jar
```

When it runs, it will print out a line for each person that gets transforms. At the end, after the job has run, we can also see the output from querying the database.

Congratulations! You have just built a batch job to ingest data from a spreadsheet, processed it, and written it into a database.

[zip]: https://github.com/springframework-meta/gs-batch-processing/archive/master.zip
[jdk7]: http://docs.oracle.com/javase/7/docs/webnotes/install/index.html
