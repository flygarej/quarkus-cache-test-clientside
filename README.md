# quarkus-cache-test-clientside

This application tests behaviour of @CacheResult and Uni's from a slow backend 
service. The issue seen is that caching does not happen until Uni is resolved, 
which allows several Uni's to run concurrently.

The result is that the last Uni to write to the cache sets the cache value on 
top of all the others.

This is handled by detecting cache is invalid and reusing the first uni to 
serve the initial call result to all entities waiting.

The application expects to be run on localhost:8080 and implements the 
following calls:

- /test/gettoken returns an Uni that should be cached. The result is
  delayed by the quarkus-cache-token-service for 10 seconds.
  This gives time to run several calls concurrently.

- /test/invalidate which invalidates the /test/gettoken cache.
  This call will NOT invalidate the cache if there is a pending call to
  /test/gettoken
  
- /test/gettokenb regular blocking call to backend service with 10 second delay.
- /test/invalidateb regular invalidate of the blocking call cache entry

- /test/gettokennaive returns an Uni that is NOT guarded wrt concurrent calls
- /test/invalidatenaive invalidate for above cache entry


# Things to test

## Setup for tests

Start the quarkus-cache-token-service and quarkus-cache-test-service in separate
terminals.

Start two more terminals to run the concurrent calls

## Normal cache blocking call

In each of the "free" terminal windows enter:

curl -v http://localhost:8080/test/gettokenb

Start both calls within 10 seconds.
The result is that the one that started first gets its response which is then 
written to the cache and returned to the second call. 
Both get the same token value returned.

## Naive reactive call

In each of the "free" terminal windows enter:

curl -v http://localhost:8080/test/gettokennaive

Start both calls within 10 seconds.
Both calls are delayed for 10 seconds and receive different tokens.
This is because the cache entry is not set until the Uni has been
resolved, which in turn is delayed by the call to the external service.

It's also possible to get weird cache effects by first calling

curl -v http://localhost:8080/test/gettokennaive

and then 

curl -v http://localhost:8080/test/invalidatenaive 

within the 10 second delay. The first call is then not cached at all.

## Fixed reactive call

In each of the "free" terminal windows enter:

curl -v http://localhost:8080/test/gettoken

and start within 10 seconds of each other

The behaviour is now similar to the regular blocking call.

I do this by detecting we're waiting for an Uni and instead of creating new 
ones I simply return the first one created so that each thread just subsribes
to the same uni. After resolution the cache will serve new calls from the cache
as usual.
The /test/invalidate also checks if a uncached call is in progress and if so, 
does NOT invalidate the cache, so that the call in progress will remain in
"charge" of the cache.

## Comments

This was written as a quick fix to the "Cache will not be written until Uni
resolves" in situations where I might have more than one call queuing for the 
same value from a slow backend. There was trial and horror involved, remains 
of which may still be in the code. Please be kind. 
