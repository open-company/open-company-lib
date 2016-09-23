# OpenCompany Lib 

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](https://travis-ci.org/open-company/open-company-lib.svg?branch=master)](https://travis-ci.org/open-company/open-company-lib)
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


## Usage

Include the following in your dependencies:

[![Clojars Project](https://img.shields.io/clojars/v/open-company/lib.svg)](https://clojars.org/open-company/lib)

To use local changes to the lib here in other projects in a development situation, build the lib and install it
locally with:

```
boot build
```

Make sure your lein or boot environment is configured to use the local Maven repository.

### Clojars

The normal case is for a project to use this library from [Clojars](https://clojars.org/open-company/lib). To push a new version to Clojars:

```
boot build push
```


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-lib):

[![Build Status](https://travis-ci.org/open-company/open-company-lib.svg?branch=master)](https://travis-ci.org/open-company/open-company-lib)

To run the tests locally:

```console
boot test!
```

## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-web/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/). See `LICENSE` for full text.

Copyright © 2016 OpenCompany, LLC.