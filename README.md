## **Work in progress** 

Was done on linux (ubuntu) 

tentative todo:

### Docker install (rabbitmq)

curl -fsSL https://get.docker.com -o get-docker.sh 


sudo sh get-docker.sh


	sudo docker run -d \
  	--hostname rabbit-svr \
  	--name thesis-rmq \
  	-p 8000:15672 \
  	-p 5672:5672 \
  	-e RABBITMQ_DEFAULT_USER=user \
  	-e RABBITMQ_DEFAULT_PASS=password \
  	rabbitmq:3-management

### Install Intellij IDEA community edition
https://www.jetbrains.com/idea/download/?section=windows


clone then run MainApp at nis1-thesis-core
