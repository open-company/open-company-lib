# OpenCompany Lib 

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](https://travis-ci.org/open-company/open-company-lib.svg)](https://travis-ci.org/open-company/open-company-lib)
[![Roadmap on Trello](http://img.shields.io/badge/roadmap-trello-blue.svg?style=flat)](https://trello.com/b/3naVWHgZ/open-company-development)


## Background

> Today, power is gained by sharing knowledge, not hoarding it.

> -- [Dharmesh Shah](https://twitter.com/dharmesh)

Employees and investors, co-founders and execs, they all want more transparency from their startups, but there's no consensus about what it means to be transparent. OpenCompany is a platform that simplifies how key business information is shared with stakeholders.

When information about growth, finances, ownership and challenges is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. OpenCompany makes it easy for founders to engage with employees and investors, creating a sense of ownership and urgency for everyone.

[OpenCompany](https://opencompany.com/) is GitHub for the rest of your company.

To maintain transparency, OpenCompany information is always accessible and easy to find. Being able to search or flip through prior updates empowers everyone. Historical context brings new employees and investors up to speed, refreshes memories, and shows how the company is evolving over time.

Transparency expectations are changing. Startups need to change as well if they are going to attract and retain savvy employees and investors. Just as open source changed the way we build software, transparency changes how we build successful startups with information that is open, interactive, and always accessible. The OpenCompany platform turns transparency into a competitive advantage.

Like the open companies we promote and support, the [OpenCompany](https://opencompany.com/) platform is completely transparent. The company supporting this effort, OpenCompany, LLC, is an open company. The [platform](https://github.com/open-company/open-company-web) is open source software, and open company data is [open data](https://en.wikipedia.org/wiki/Open_data) accessible through the [platform API](https://github.com/open-company/open-company-api).

To get started, head to: [OpenCompany](https://opencompany.com/)


## Overview

The OpenCompany Lib project provides a few namespaces which are shared among multiple OpenCompany projects.


## Local Setup

Users of the [OpenCompany](https://opencompany.com/) platform should get started by going to [OpenCompany](https://opencompany.com/). The following local setup is **for developers** wanting to work on the storage service.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8+ JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) 2.7.1+ - Clojure's build and dependency management tool

#### Java

Chances are your system already has Java 8+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 8+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

#### Leiningen

Leiningen is easy to install:

1. Download the latest [lein script from the stable branch](https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein).
1. Place it somewhere that's on your $PATH (`env | grep PATH`). `/usr/local/bin` is a good choice if it is on your PATH.
1. Set it to be executable. `chmod 755 /usr/local/bin/lein`
1. Run it: `lein` This will finish the installation.

Then let Leiningen install the rest of the dependencies:

```console
git clone https://github.com/open-company/open-company-api.git
cd open-company-api
lein deps
```


## Usage

To use this library in your other projcets, include the following in your dependencies:

[![Clojars Project](https://img.shields.io/clojars/v/open-company/lib.svg)](https://clojars.org/open-company/lib)

To use local changes to the lib here in other projects in a development situation, build the lib and install it
locally with:

```
lein install
```

Make sure your lein or boot environment is configured to use the local Maven repository.

## Development

To start a REPL for local development on this library, run:

```
lein repl
```

### Pushing to Clojars

The normal case is for a project to use this library from [Clojars](https://clojars.org/open-company/lib). To push a new version to Clojars, first authorize the release by setting your credentials:

```console
export CLOJARS_USER="<your Clojars username>"
export CLOJARS_PASS="<your Clojars password>"
```

Then build and push the lib:

```
lein deploy release
```

You can't have any changed or untracked files in your local repo or you'll get an `Assert failed: project repo is not clean`
error.

## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-lib):

[![Build Status](https://travis-ci.org/open-company/open-company-lib.svg)](https://travis-ci.org/open-company/open-company-lib)


To run the tests locally:

```console
lein test!
```

To run tests watching for local changes during development:

```console
lein autotest
```

## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-web/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/). See `LICENSE` for full text.

Copyright © 2016-2017 OpenCompany, LLC.
