FROM java:8

WORKDIR /

COPY calc_fib.jar .

CMD java -jar calc_fib.jar /mount/input.csv && cp /output.csv /mount/output.csv