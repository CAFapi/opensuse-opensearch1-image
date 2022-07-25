#
# Copyright 2022 Micro Focus or one of its affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Docker registry
ARG DOCKER_HUB_PUBLIC=docker.io

# Reference the official OpenSearch 1 image
FROM ${DOCKER_HUB_PUBLIC}/opensearchproject/opensearch:1.3.4 AS os1-image

RUN rm -rf /usr/share/opensearch/jdk \
           /usr/share/opensearch/lib/log4j-*

#
# The actual image definition
#
FROM ${DOCKER_HUB_PUBLIC}/cafapi/opensuse-jre11:3

RUN zypper -n refresh && \
    zypper -n update

COPY --from=os1-image /etc/pki/ca-trust /etc/pki/ca-trust

# Install packages
RUN zypper -n install which hostname netcat-openbsd unzip wget
RUN zypper -n clean --all

RUN mkdir -p /usr/share/opensearch
# Create group, user and set access rights to the opensearch base dir
RUN groupadd -g 1000 opensearch && \
    useradd -u 1000 -g 1000 -G 0 -d /usr/share/opensearch opensearch && \
    chmod 0775 /usr/share/opensearch && \
    chgrp 0 /usr/share/opensearch

WORKDIR /usr/share/opensearch

COPY --from=os1-image --chown=1000:0 /usr/share/opensearch/ /usr/share/opensearch/

# Configure logging
COPY log4j2.properties /usr/share/opensearch/config
COPY /maven/*.jar /usr/share/opensearch/lib/

# Install ICU Analysis plugin
RUN JAVA_HOME=${JRE_HOME} OPENSEARCH_JAVA_OPTS="$(echo ${https_proxy} | sed -E 's/^.*:\/\/(.*):([0-9]*).*/-Dhttps.proxyHost=\1 -Dhttps.proxyPort=\2/')" \
    /usr/share/opensearch/bin/opensearch-plugin install analysis-icu

ENV PATH=/usr/share/opensearch/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

COPY --from=os1-image --chown=1000:0 /usr/share/opensearch/opensearch-docker-entrypoint.sh /usr/share/opensearch/opensearch-docker-entrypoint.sh
RUN chgrp 0 /usr/share/opensearch/opensearch-docker-entrypoint.sh && \
    chmod g=u /etc/passwd && \
    chmod 0775 /usr/share/opensearch/opensearch-docker-entrypoint.sh

COPY scripts/ /opt/opensearch/scripts/
RUN chmod +x /opt/opensearch/scripts/*

# Startup opensearch and expose ports
EXPOSE 9200 9300

HEALTHCHECK --interval=1m --start-period=5m CMD curl -k -u "admin:admin" https://0.0.0.0:9200 || exit 1

CMD /opt/opensearch/scripts/start.sh
