# fetcher-proxy
fetcher-proxy is a simple http proxy based on spray. It supports GET/POST/HEAD ..., except CONNECT.

It's lifecycle is defined carefully. Now it is a standalone application. but you can embed it into your application easily. 

## How to Build It?
* git clone https://github.com/woshilaiceshide/fetcher-proxy.git
* cd ./fetcher-proxy
* sbt dist

## How to Configure It?
Please see https://github.com/woshilaiceshide/fetcher-proxy/blob/master/conf/application.conf

The main options is as below: 
	
	fetcher-proxy {
		interface = "0.0.0.0"
		port = "8787"
		wait_for_x_seconds_when_stop = 9
	}


## Little Test
wget --max-redirect=0 -e use_proxy=yes -e http_proxy=127.0.0.1:8787 http://scala-lang.org/documentation/

## Special Paths
The following paths are treated specially: 

1.
**/@ping**

response: PONG!

2.
**/@server-stats**

response: stats of the server

3.
**/@404**

response: "unknown resource" with 404

## Enjoy It
Any feedback is expected.


