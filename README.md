
What you'll build
-----------------

This guide walks you through creating a basic batch-driven solution. You build a service that imports data from a CSV spreadsheet, transforms it with custom code, and stores the final results in a database.

What you'll need
----------------

 - About 15 minutes
 - A favorite text editor or IDE
 - [JDK 6][jdk] or later
 - [Maven 3.0][mvn] or later

[jdk]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[mvn]: http://maven.apache.org/download.cgi

How to complete this guide
--------------------------

Like all Spring's [Getting Started guides](/guides/gs), you can start from scratch and complete each step, or you can bypass basic setup steps that are already familiar to you. Either way, you end up with working code.

To **start from scratch**, move on to [Set up the project](#scratch).

To **skip the basics**, do the following:

 - [Download][zip] and unzip the source repository for this guide, or clone it using [git](/understanding/git):
`git clone https://github.com/springframework-meta/gs-batch-processing.git`
 - cd into `gs-batch-processing/initial`.
 - Jump ahead to [Create a business class](#initial).

**When you're finished**, you can check your results against the code in `gs-batch-processing/complete`.
[zip]: https://github.com/springframework-meta/gs-batch-processing/archive/master.zip


<a name="scratch"></a>
Set up the project
------------------
First you set up a basic build script. You can use any build system you like when building apps with Spring, but the code you need to work with [Maven](https://maven.apache.org) and [Gradle](http://gradle.org) is included here. If you're not familiar with either, refer to [Building Java Projects with Maven](/guides/gs/maven/content) or [Building Java Projects with Gradle](/guides/gs/gradle/content).

### Create the directory structure

In a project directory of your choosing, create the following subdirectory structure; for example, with `mkdir -p src/main/java/hello` on *nix systems:

    └── src
        └── main
            └── java
                └── hello

### Create a Maven POM

`pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>org.springframework</groupId>
	<artifactId>gs-batch-processing</artifactId>
	<version>0.1.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>0.5.0.BUILD-SNAPSHOT</version>
    </parent>
    
	<dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-batch</artifactId>
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

This guide is using [Spring Boot's starter POMs](/guides/gs/spring-boot/content).

Note to experienced Maven users who are unaccustomed to using an external parent project: you can take it out later, it's just there to reduce the amount of code you have to write to get started.

### Create business data

Typically your customer or a business analyst supplies a spreadsheet. In this case, you make it up.

`src/main/resources/sample-data.csv`
```csv
Jill,Doe
Joe,Doe
Justin,Doe
Jane,Doe
John,Doe
```

This spreadsheet contains a first name and a last name on each row, separated by a comma. This is a fairly common pattern that Spring handles out-of-the-box, as you will see.

### Define the destination for your data

Next, you write a SQL script to create a table to store the data.

`src/main/resources/schema-all.sql`
```sql
DROP TABLE people IF EXISTS;

CREATE TABLE people  (
	person_id BIGINT IDENTITY NOT NULL PRIMARY KEY,
	first_name VARCHAR(20),
	last_name VARCHAR(20)
);
```

> **Note:** Spring Boot runs `schema-@@platform@@.sql` automatically during startup. `-all` is the default for all platforms.

<a name="initial"></a>
Create a business class
-----------------------

Now that you see the format of data inputs and outputs, you write code to represent a row of data.

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

You can instantiate the `Person` class either with first and last name through a constructor, or by setting the properties.

Create an intermediate processor
--------------------------------

A common paradigm in batch processing is to ingest data, transform it, and then pipe it out somewhere else. Here you write a simple transformer that converts the names to uppercase.

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

`PersonItemProcessor` implements Spring Batch's `ItemProcessor` interface. This makes it easy to wire the code into a batch job that you define further down in this guide. According to the interface, you receive an incoming `Person` object, after which you transform it to an upper-cased `Person`.

> **Note:** There is no requirement that the input and output types be the same. In fact, after one source of data is read, sometimes the application's data flow needs a different data type.

Put together a batch job
----------------------------

Now you put together the actual batch job. Spring Batch provides many utility classes that reduce the need to write custom code. Instead, you can focus on the business logic.

`src/main/java/hello/BatchConfiguration.java`
```java
package hello;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
import org.springframework.boot.SpringApplication;
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

For starters, the `@EnableBatchProcessing` annotation adds many critical beans that support jobs and saves you a lot of leg work.

Break it down:

`src/main/java/hello/BatchConfiguration.java`
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

The first chunk of code defines the input, processor, and output.
- `reader()` creates an `ItemReader`. It looks for a file called `sample-data.csv` and parses each line item with enough information to turn it into a `Person`.
- `processor()` creates an instance of our `PersonItemProcessor` you defined earlier, meant to uppercase the data.
- `write(DataSource)` creates an `ItemWriter`. This one is aimed at a JDBC destination and automatically gets a copy of the dataSource created by `@EnableBatchProcessing`. It includes the SQL statement needed to insert a single `Person` driven by java bean properties.

The next chunk focuses on the actual job configuration.

`src/main/java/hello/BatchConfiguration.java`
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

The first method defines the job and the second one defines a single step. Jobs are built from steps, where each step can involve a reader, a processor, and a writer. 

In this job definition, you need an incrementer because jobs use a database to maintain execution state. You then list each step, of which this job has only one step. The job ends, and the java API produces a perfectly configured job.

In the step definition, you define how much data to write at a time. In this case, it writes up to ten records at a time. Next, you configure the reader, processor, and writer using the injected bits from earlier. 

> **Note:** chunk() is prefixed `<Person,Person>` because it's a generic method. This represents the input and output types of each "chunk" of processing, and lines up with `ItemReader<Person>` and `ItemWriter<Person>`.

Finally, you run the application.

`src/main/java/hello/BatchConfiguration.java`
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

This example uses a memory-based database (provided by `@EnableBatchProcessing`), meaning that when it's done, the data is gone. For demonstration purposes, there is extra code to create a `JdbcTemplate`, query the database, and print out the names of people the batch job inserts.

Now that your `Application` class is ready, you simply instruct the build system to create a single, executable jar containing everything. This makes it easy to ship, version, and deploy the service as an application throughout the development lifecycle, across different environments, and so forth.

Add the following configuration to your existing Maven POM:

`pom.xml`
```xml
    <properties>
        <start-class>hello.Application</start-class>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

The `start-class` property tells Maven to create a `META-INF/MANIFEST.MF` file with a `Main-Class: hello.Application` entry. This entry enables you to run it with `mvn spring-boot:run` (or simply run the jar itself with `java -jar`).

The [Spring Boot maven plugin][spring-boot-maven-plugin] collects all the jars on the classpath and builds a single "über-jar", which makes it more convenient to execute and transport your service.

Now run the following command to produce a single executable JAR file containing all necessary dependency classes and resources:

```sh
$ mvn package
```

[spring-boot-maven-plugin]: https://github.com/SpringSource/spring-boot/tree/master/spring-boot-maven-plugin

> **Note:** The procedure above will create a runnable JAR. You can also opt to [build a classic WAR file](/guides/gs/convert-jar-to-war/content) instead.

Run the batch job
-------------------
Run your batch job using the spring-boot plugin at the command line:

```sh
$ mvn spring-boot:run
```


The job prints out a line for each person that gets transformed. After the job runs, you can also see the output from querying the database.

Summary
-------

Congratulations! You built a batch job that ingested data from a spreadsheet, processed it, and wrote it to a database.
