
==================================
Android OpenID Connect Proxy (AOP)
==================================

A local smartphone module acting as an OpenID Connect Server proxy and delivers tokens to installed native applications. The AOP improves the user experience with single sign on, uniform authentication and interface.

Description
-----------
The AOP project is a set of three projects:  an Android service (oidc_secure_proxy), a client app (HelloOidcProxy) enabling to test the AOP services, and an OpenID Connect Server (PhpOpSecureProxy).
The OIDC server is based on an `open source implementation of OpenID Connect in PHP by Nomura Research Institute, Ltd`_.

The AOP receives requests from native applications needing to access user personal data and requests the server to obtain tokens on behalf of the native app. After receiving the tokens the AOP forwards them to the native app which can then use them.
    
.. _`open source implementation of OpenID Connect in PHP by Nomura Research Institute, Ltd`: https://bitbucket.org/PEOFIAMP/phpoidc/


References
----------
* `OpenID Connect protocol`_ 
* `OpenID Connect Server Implementation (PHP)`_ (phpOIDC Project, commit number 6ac8e6d from 2014-09-05)
* Smart Card API for Android `Seek For Android`_ 
* Cryptography Libraries for Android `Spongy castle libs from Roberto Tyley`_

.. _`OpenID Connect protocol`: http://openid.net/connect/   
.. _`Seek For Android`: https://code.google.com/p/seek-for-android/wiki/Index
.. _`OpenID Connect Server Implementation (PHP)`: https://bitbucket.org/PEOFIAMP/phpoidc/
.. _`Spongy castle libs from Roberto Tyley`: https://github.com/rtyley/spongycastle



Development Tools
-----------------
* Android Development: `Eclipse`_ + `Android ADT plugin`_
* Server Development: Any PHP Server, `Easy PHP`_ is a good one

.. _`Eclipse`: https://eclipse.org/downloads/
.. _`Android ADT plugin`: http://developer.android.com/tools/sdk/eclipse-adt.html
.. _`Easy PHP`: http://www.easyphp.org/
Required Equipment
-------------------

* For Android development: a `compatible android device`_

.. _`compatible android device`: https://code.google.com/p/seek-for-android/wiki/Devices


Installation
------------
After downloading and setting up the development environments, download every part of the project (OIDC Server, Android service and the test app)

Import the projects in the corresponding environments, for example: the Android Service and the test app in Eclipse + ADT.
For the OIDC server, follow the steps described in `the phpOIDC project`_ and then replace the corresponding files with the OIDC project files.
  
.. _`the phpOIDC project`: https://bitbucket.org/PEOFIAMP/phpoidc/


AOP Sequence Diagram
====================

.. image:: https://cloud.githubusercontent.com/assets/11352074/7454573/5555602e-f275-11e4-9b2c-75ba24d28a53.png


License
-------

Copyright © 2015 Orange

This project is licensed under the Apache License, Version 2.0 (the "License");
you may not use it except in compliance with the License.
You may obtain a copy of the License `here`_

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

.. _`here`: http://www.apache.org/licenses/LICENSE-2.0
