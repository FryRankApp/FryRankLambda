# Python scripts for FryRank Lambda project

This folder contains all the python quick scripts used for or complimenting various testings of the FryRank backend.

## Seeding Reviews
**seed_reviews.py** - Inject dummy reviews into the FryRank DynamoDB backend for testing.

To use, input the following command into the terminal after authenticating access with your selected sandbox/env:  
`python seed_reviews.py --restaurant-id <id> --count <n> [--region <region>]`

- Each review gets a unique fake accountId, a random score (1.0-10.0), and a
timestamp spread evenly over the past <count> hours so pagination ordering is
deterministic and easy to reason about.

- The script also creates/updates the AGGREGATE row for the restaurant so that
aggregate stats remain consistent.



## Nuking Reviews:
**!!!WARNING: This is destructive and irreversible. Please DO NOT use on prod environments!!!**

**nuke_reviews.py** - Delete all reviews (and optionally the AGGREGATE row) for a restaurant ID.

To use, input the following command into the terminal after authenticating access with your selected sandbox/env:  
`python nuke_reviews.py --restaurant-id <id> [--keep-aggregate] [--region <region>]`

Queries all items under the given restaurantId and batch-deletes every REVIEW item.
Also deletes the AGGREGATE row unless --keep-aggregate is passed.