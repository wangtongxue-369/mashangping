FROM python:3.11-slim
RUN useradd --uid 2000 --create-home judge \
 && mkdir -p /work && chown judge:judge /work
USER judge
WORKDIR /work
