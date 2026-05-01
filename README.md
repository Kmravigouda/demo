# LeetCode Stats Portal

A Spring Boot web app that lets you upload a student list and download a ranked CSV report with:

- Rank
- Name
- LeetCode ID
- Max streak
- Total problems solved
- Total contests attended

## Input format

Accepted file types: `.csv`, `.txt`

Supported line formats:

```text
Name,LeetCode ID
Aarav,aarav_123
Diya,diya_codes
```

or

```text
LeetCode ID
aarav_123
diya_codes
```

## Run locally

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`

## Build

```bash
mvn clean package
```

The app stores generated CSV files in `generated-reports/`.
