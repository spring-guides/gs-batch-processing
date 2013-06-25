# Getting Started: Creating a Batch Service

What you'll build
-----------------

This guide walks you through creating a basic batch-driven solution. You build a service that imports data from a CSV spreadsheet, transforms it with custom code, and stores the final results in a database.

What you'll need
----------------

 - About 15 minutes
 - {!include#prereq-editor-jdk-buildtools}

## {!include#how-to-complete-this-guide}

<a name="scratch"></a>
Set up the project
------------------
{!include#build-system-intro}

{!include#create-directory-structure-hello}

### Create a Maven POM

    {!include:initial/pom.xml}

{!include#bootstrap-starter-pom-disclaimer}

### Create some business data

Typically a spreadsheet is supplied by your customer or a business analyst. In this case, you make it up.

`src/main/resources/sample-data.csv`
```text
Jill,Doe
Joe,Doe
Justin,Doe
Jane,Doe
John,Doe
```

This spreadsheet contains a first name and a last name on each row, separated by a comma. This is a fairly common pattern that Spring handles out-of-the-box, as you will see.

### Define the destination for your data

Next, you write a SQL script to create a table to store the data.

    {!include:complete/src/main/resources/schema.sql}

<a name="initial"></a>
Create a business class
-----------------------

Now that you see the format of data inputs and outputs, you write code to represent a row of data.

    {!include:complete/src/main/java/hello/Person.java}

You can instantiate the `Person` class either with first and last name through a constructor, or by setting the properties.

Create an intermediate processor
--------------------------------

A common paradigm in batch processing is to ingest data, transform it, and then pipe it out somewhere else. Here you write a simple transformer that converts the names to uppercase.

    {!include:complete/src/main/java/hello/PersonItemProcessor.java}

`PersonItemProcessor` implements Spring Batch's `ItemProcessor` interface. This makes it easy to wire the code into a batch job that you define further down in this guide. According to the interface, you receive an incoming `Person` object, after which you transform it to an upper-cased `Person`.

> Note: There is no requirement that the input and output types be the same. In fact, after one source of data is read, sometimes the application's data flow needs a different data type.

Put together a batch job
----------------------------

Now you put together the actual batch job. Spring Batch provides many utility classes that reduce the need to write custom code. Instead, you can focus on the business logic.

    {!include:complete/src/main/java/hello/BatchConfiguration.java}

For starters, the `@EnableBatchProcessing` annotation adds many critical beans that support jobs and save you a lot of leg work.

Break it down:

    {!include:complete/src/main/java/hello/BatchConfiguration.java#reader-writer-processor}

The first chunk of code defines the input, processor, and output.
- `reader()` creates an `ItemReader`. It looks for a file called `sample-data.csv` and parses each line item with enough information to turn it into a `Person`.
- `processor()` creates an instance of our `PersonItemProcessor` you defined earlier, meant to uppercase the data.
- `write(DataSource)` creates an `ItemWriter`. This one is aimed at a JDBC destination and automatically gets a copy of the dataSource created by `@EnableBatchProcessing`. It includes the SQL statement needed to insert a single `Person` driven by java bean properties.

The next chunk focuses on the actual job configuration.

    {!include:complete/src/main/java/hello/BatchConfiguration.java#job-step}

The first method defines the job and the second one defines a single step. Jobs are built from steps, where each step can involve a reader, a processor, and a writer. 

In this job definition, you need an incrementer because jobs use a database to maintain execution state. You then list each step, of which this job has only one step. The job ends, and the java API produces a perfectly configured job.

In the step definition, you define how much data to write at a time. In this case, it writes up to ten records at a time. Next, you configure the reader, processor, and writer using the injected bits from earlier. Finally, the builder API turns it into a nicely built step.

> chunk() is prefixed `<Person,Person>` because it's a generic method. This represents the input and output types of each "chunk" of processing, and lines up with `ItemReader<Person>` and `ItemWriter<Person>`.

Finally, you run the application.

    {!include:complete/src/main/java/hello/BatchConfiguration.java#template-main}

This example uses a memory-based database (provided by `@EnableBatchProcessing`), meaning that when it's done, the data is gone. For demonstration purposes, there is extra code to create a `JdbcTemplate` and query the database, and print out the names of people the batch job inserts.


Build an executable JAR
-----------------------
Add the following to your `pom.xml` file (keeping existing properties and plugins intact):

    {!include:complete/pom.xml#shade-config}

Create a single executable JAR file containing all necessary dependency classes:

    $ mvn package


Run the batch job
-----------------

Now you can run the job from the JAR as well, and distribute that as an executable artifact:

    $ java -jar target/gs-batch-processing-complete-0.1.0.jar


The job prints out a line for each person that gets transformed. At the end, after the job has run, you can also see the output from querying the database.

Summary
-------

Congratulations! You built a batch job that ingested data from a spreadsheet, processed it, and wrote it to a database.
