<div align="center">

<img src="docs/assets/logo.webp" width="280" alt="Inertia4J" align="center"/></a>

<h2>Inertia4J</h2> 
<p>
Server-side <a href="https://inertiajs.com/">Inertia.js</a> adapter for JVM backends.
</p>
<div>
<a href="https://github.com/arnaud-ritti/inertia4j/actions/workflows/ci.yml"><img src="https://github.com/arnaud-ritti/inertia4j/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"/></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/arnaud-ritti/inertia4j.svg?style=flat" alt="license"/></a>
</div>

</div>

## About Inertia4J

Inertia provides a protocol to render components written in modern frontend frameworks such as React, Vue.js, and Angular, directly from the backend. Inertia4J is a JVM adapter implementing such a protocol, and currently offers first-class support for Spring and Ktor. Below, you can find usage instructions for each framework.

* [Using with Spring Boot 3 (Java / Kotlin)](/inertia4j.spring-boot-3/README.md)
* [Using with Spring Boot 4 (Java / Kotlin)](/inertia4j.spring-boot-4/README.md)
* [Using with Ktor (Kotlin)](/inertia4j.ktor/README.md)
* [Generating TypeScript types for props](/docs/typescript.md)
* [Advanced Usage and Extending Inertia4J](#advanced-usage-and-extending-inertia4j)
* [Contributing](#contributing)
* [License](#license)

### Advanced Usage and Extending Inertia4J

Inertia4J was designed from the start to be easy to use for small projects, yet fully customizable and modular, so you can tweak the library to fit your project's needs. If you want to learn about customizing and extending Inertia4J, you
can read the [advanced usage guide](docs/advanced.md).

### Contributing

Inertia4J is an open source project maintained by [@arnaud-ritti](https://github.com/arnaud-ritti). If you'd like to contribute with any new features or bug fixes, please read the
[contributing guide](CONTRIBUTING.md) and the [code of conduct](CODE_OF_CONDUCT.md). Security issues should be reported
privately as described in the [security policy](SECURITY.md).

### License

Inertia4J is released under the [Apache License 2.0](LICENSE).
