FROM clojure
LABEL maintainer="erewl"
 
ENV CONSUMER_KEY "$CONSUMER_KEY"                            
ENV CONSUMER_SECRET "$CONSUMER_SECRET"                            
ENV ACCESS_TOKEN "$ACCESS_TOKEN"                            
ENV ACCESS_TOKEN_SECRET "$ACCESS_TOKEN_SECRET"                            

# Create the project and download dependencies.
WORKDIR /twitter-bot/src
COPY project.clj .
RUN lein deps

# Copy local code to the container image.
COPY . .

# Build an uberjar release artifact.
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
