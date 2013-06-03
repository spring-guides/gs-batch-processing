# Getting Started: Batch-based Processing

What you'll build
-----------------

This guide will take you through creating a very basic batch-driven solution. We'll build a service that imports data from a CSV spreadsheet, transforms it with some custom code, and stores the final results in a database.

What you'll need
----------------

 - About 15 minutes
 - {!include#prereq-editor-jdk-buildtools}

{!include#how-to-complete-this-guide}

<a name="scratch"></a>
Set up the project
------------------
{!include#build-system-intro}

### Create a Maven POM

{!include#maven-project-setup-options}

    {!include:initial/pom.xml}

{!include#bootstrap-starter-pom-disclaimer}

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

    {!include:complete/src/main/resources/schema.sql}

<a name="initial"></a>
Create a business class
-----------------------

Now that we see the format of inputs and outputs for our data, let's write some code to represent a row of data.

    {!include:complete/src/main/java/hello/Person.java}

The `Person` class can either be instantiated with first and last name through a constructor or by setting the properties.

Create an intermediate processor
--------------------------------

A common paradigm in batch processing is to ingest data, transform it, and then pipe it out somewhere else. Let's write a simple transformer that converts the names to uppercase.

    {!include:complete/src/main/java/hello/PersonItemProcessor.java}

`PersonItemProcessor` implements Spring Batch's `ItemProcessor` interface. This makes it easy to wire the code into a batch job we'll define further down in this guide. According to the interface, we will get handed an incoming `Person` object, after which we will transform it to an upper-cased `Person`.

> There is no requirement that the input and output types be the same. In fact, it is often the case that after reading one source of data, and different data type is what's needing in the application's data flow.

Putting together a batch job
----------------------------

Now let's put together the actual batch job. Spring Batch provides many utility classes that reduces our need to write custom code. Instead, we can focus on the business logic.

    {!include:complete/src/main/java/hello/BatchConfiguration.java}

For starters, the `@EnableBatchProcessing` annotation adds many critical beans that support jobs, saving us a lot of leg work.

Let's break this down:

    {!include:complete/src/main/java/hello/BatchConfiguration.java#reader-writer-processor}

This first chunk of code defines the input, processor, and output.
- `reader()` creates an `ItemReader`. It looks for a file called `sample-data.csv` and parses each line item with enough information to turn it into a `Person`.
- `processor()` creates an instance of our `PersonItemProcessor` we defined earlier, meant to uppercase the data.
- `write(DataSource)` creates an `ItemWriter`. This one is aimed at a JDBC destination and automatically gets a copy of the dataSource created by `@EnableBatchProcessing`. It includes the SQL statement needed to insert a single `Person` driven by java bean properties.

The next chunk is focused on the actual job configuration.

    {!include:complete/src/main/java/hello/BatchConfiguration.java#job-step}

The first method defines our job and the second one defines a single step. Jobs are built out of steps, where each step can involved a reader, a processor, and a writer. 

In our job definition, we need an incrementer because jobs use a database to maintain execution state. We then list each of the steps, of which our job has just one step. Finally, the job has an end. With all this, the java API spits out a perfectly configured job.

In our step definition, we define how much data to write at a time. In this case, it writes up to ten records at a time. Next, we configure the reader, processor, and writer using the injected bits from earlier. Finally, the builder API turns it into a nicely built step.

> chunk() is prefixed `<Person,Person>` because it's a generic method. This represents the input and output types of each "chunk" of processing, and lines up with `ItemReader<Person>` and `ItemWriter<Person>`.

Finally, we need the part that runs our application.

    {!include:complete/src/main/java/hello/BatchConfiguration.java#template-main}

This example uses a memory-based database (provided by `@EnableBatchProcessing`), meaning that when it's all done, the data will be gone. For demonstration purposes, there is a little extra code to create a `JdbcTemplate` and query the database, printing out all the people our batch job inserts.


Build an executable JAR
-----------------------
Add the following to your `pom.xml` file (keeping existing properties and plugins intact):

    {!include:complete/pom.xml#shade-config}

The following will produce a single executable JAR file containing all necessary dependency classes:

    $ mvn package


Run the batch job
-----------------

Now you can run it from the jar as well, and distribute that as an executable artifact:

    $ java -jar target/gs-batch-processing-complete-0.1.0.jar


When it runs, it will print out a line for each person that gets transforms. At the end, after the job has run, we can also see the output from querying the database.

Congratulations! You have just built a batch job to ingest data from a spreadsheet, processed it, and written it into a database.
