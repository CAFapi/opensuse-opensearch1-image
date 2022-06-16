# opensuse-opensearch1-image

This project uses the openSUSE Java 11 image [here](https://github.com/CAFapi/opensuse-java11-images) to build a pre-configured OpenSearch 1 Docker image.

It can be used as a base image for any projects that use OpenSearch.

### Docker host setup
[The `vm.max_map_count` kernel setting needs to be set on host to at least `262144` for production use](https://opensearch.org/docs/latest/opensearch/install/important-settings/):

`sysctl -w vm.max_map_count=262144`

### Start a continer with the image
`docker run -d --name os1 -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" cafapi/opensuse-opensearch1`

Send requests to the server to verify that OpenSearch is up and running:

`curl -ku -u 'admin:admin' https://localhost:9200`  
`curl -ku -u 'admin:admin' https://localhost:9200/_cat/nodes?v`  
`curl -ku -u 'admin:admin' https://localhost:9200/_cat/plugins?v`
