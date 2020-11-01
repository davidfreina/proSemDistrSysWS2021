#Measurements

We measure the time to upload, execute and download everything from the VM to our local machine

## Task 3

Full took: 259340ms

## Task 4

Half one took: 115494ms

Half two took: 170554ms

Because of parallel execution on two VM's the whole calculation took therefore: 170554ms

## Task 5

### Even-Split

We initially tried splitting the numbers as evenly as possible which lead to this:

| half_one | half_two |
|----------|----------|
| 1        | 47       |
| 46       | 2        |
| 3        | 45       |
| 44       | 4        |
| 5        | 43       |
| 42       | 6        |
| 7        | 41       |
| 40       | 8        |
| 9        | 39       |
| 38       | 10       |
| 11       | 37       |
| 36       | 12       |
| 13       | 35       |
| 34       | 14       |
| 15       | 33       |
| 32       | 16       |
| 17       | 31       |
| 30       | 18       |
| 19       | 29       |
| 28       | 20       |
| 21       | 27       |
| 26       | 22       |
| 23       | 25       |
| 24       | -        |

Which lead to the time we presented in task 4.

### Even-Odd-Split

We decided to split the data in odd and even numbers which lead to the following times:

Half one (odd) took: 173576ms

Half two (even) took: 114456ms

### Half-Split

The next test was splitting the numbers just in half so half one is 1-23 and half two is 24-47. This lead to the follwing times:

Half one (1-23) took: 22981ms

Half two (24-47) took: 262805ms

### Best-Split

The last test was splitting the numbers so that we would get almost the same times for half one and half two therefore lowering the overall time to the minimum.

Half one (1-40,47) took: 171192ms

Half two (41-46) took: 114430ms