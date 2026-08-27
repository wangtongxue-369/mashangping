FROM gcc:12
RUN useradd --uid 2000 --create-home judge \
 && mkdir -p /work && chown judge:judge /work
USER judge
WORKDIR /work
