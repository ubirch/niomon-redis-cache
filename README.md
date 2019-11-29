# Niomon Redis Cache
Distributed caching library that utilizes redis and exposes a nice API for enriching arbitrary functions with caching
functionality.

## Development
The entrypoint of this library is the [RedisCache](./src/main/scala/com/ubirch/niomon/cache/RedisCache.scala) class.
See the ScalaDoc there.

### Core libraries
* redisson