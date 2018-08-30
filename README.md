# [OpenCompany](https://github.com/open-company) Common Library 

[![AGPL License](http://img.shields.io/badge/license-AGPL-blue.svg?style=flat)](https://www.gnu.org/licenses/agpl-3.0.en.html)
[![Build Status](https://travis-ci.org/open-company/open-company-lib.svg)](https://travis-ci.org/open-company/open-company-lib)
[![Dependencies Status](https://versions.deps.co/open-company/open-company-lib/status.svg)](https://versions.deps.co/open-company/open-company-lib)


## Background

> Today, power is gained by sharing knowledge, not hoarding it.

> -- [Dharmesh Shah](https://twitter.com/dharmesh)

Teams struggle to keep everyone on the same page. People are hyper-connected in the moment with chat and email, but it gets noisy as teams grow, and people miss key information. Everyone needs clear and consistent leadership, and the solution is surprisingly simple and effective - **great leadership updates that build transparency and alignment**.

With that in mind we designed [Carrot](https://carrot.io/), a software-as-a-service application powered by the open source [OpenCompany platform](https://github.com/open-company) and a source-available [web UI](https://github.com/open-company/open-company-web).

With Carrot, important company updates, announcements, stories, and strategic plans create focused, topic-based conversations that keep everyone aligned without interruptions. When information is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for leaders to engage with employees, investors, and customers, creating alignment for everyone.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy teams, investors and customers. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Common Library project provides a few namespaces which are shared among multiple OpenCompany projects.


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the OpenCompany Common Library.

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

To use this library in your other projects, include the following in your dependencies:

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

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-lib/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [GNU Affero General Public License Version 3](https://www.gnu.org/licenses/agpl-3.0.en.html).

Copyright © 2016-2018 OpenCompany, LLC.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.en.html) for more details.
